package com.petcare.wallet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.user.entity.User;
import com.petcare.user.mapper.UserMapper;
import com.petcare.wallet.dto.WalletDtos.WalletAccountRow;
import com.petcare.wallet.dto.WalletDtos.WalletResponse;
import com.petcare.wallet.dto.WalletDtos.WalletTransactionResponse;
import com.petcare.wallet.entity.Wallet;
import com.petcare.wallet.entity.WalletTransaction;
import com.petcare.wallet.enums.WalletDirection;
import com.petcare.wallet.enums.WalletOperatorType;
import com.petcare.wallet.enums.WalletSourceType;
import com.petcare.wallet.mapper.WalletMapper;
import com.petcare.wallet.mapper.WalletTransactionMapper;
import com.petcare.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Default {@link WalletService} implementation.
 *
 * <p>Extends {@link ServiceImpl} so plain CRUD on {@link Wallet} is provided by MyBatis-Plus.
 * Custom methods handle balance mutations with row locking + ledger writes.</p>
 *
 * <p>Transaction strategy mirrors {@code ProductOrderTransactionServiceImpl}: all mutations run
 * under {@code @Transactional(rollbackFor = Exception.class)}; balance changes lock the wallet
 * row via {@link WalletMapper#selectForUpdate} before the conditional UPDATE.</p>
 */
@Service
@Slf4j
public class WalletServiceImpl extends ServiceImpl<WalletMapper, Wallet> implements WalletService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    // baseMapper (WalletMapper) is injected by ServiceImpl's own mechanism — do NOT declare a
    // constructor parameter for it, or Spring's constructor injection will shadow the field-based
    // autowiring and leave baseMapper null at runtime (causes NPE → 500).
    private final WalletTransactionMapper walletTransactionMapper;
    private final UserMapper userMapper;

    public WalletServiceImpl(WalletTransactionMapper walletTransactionMapper,
                              UserMapper userMapper) {
        this.walletTransactionMapper = walletTransactionMapper;
        this.userMapper = userMapper;
    }

    // ==================================================================
    // Read side
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(Long userId) {
        // Read-only path: return a zero-balance view if the user has no wallet yet.
        // Wallet rows are only created on the write path (lockWalletForUpdate), never here,
        // because this method is annotated @Transactional(readOnly = true).
        Wallet wallet = baseMapper.selectOne(
                Wrappers.<Wallet>lambdaQuery().eq(Wallet::getUserId, userId));
        if (wallet == null) {
            return new WalletResponse(null, userId, ZERO, ZERO, null);
        }
        return toWalletResponse(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> getMyTransactions(Long userId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = clampSize(size);
        LambdaQueryWrapper<WalletTransaction> q = Wrappers.<WalletTransaction>lambdaQuery()
                .eq(WalletTransaction::getUserId, userId)
                .orderByDesc(WalletTransaction::getCreateTime);
        Page<WalletTransaction> pageParam = new Page<>(safePage, safeSize);
        List<WalletTransaction> records = walletTransactionMapper.selectPage(pageParam, q).getRecords();
        return enrichTransactionsWithUser(records);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletAccountRow> listAccounts(String phoneKeyword, String userIdKeyword, int page, int size) {
        // Bug fix (CR-20260718-003): the account list must include ALL users, not only those
        // who already have a wallet row. Otherwise admins cannot find a user to recharge until
        // the user has been recharged once (chicken-and-egg). User table is the primary source;
        // wallet data is left-joined in memory.
        int safePage = Math.max(1, page);
        int safeSize = clampSize(size);
        Page<User> userPage = userMapper.selectPage(
                new Page<>(safePage, safeSize),
                buildUserQuery(phoneKeyword, userIdKeyword));
        List<User> users = userPage.getRecords();
        if (users.isEmpty()) {
            return List.of();
        }
        // Batch-load wallets for the current page of users (avoids N+1).
        List<Long> userIds = users.stream().map(User::getId).toList();
        Map<Long, Wallet> walletByUser = baseMapper.selectList(
                Wrappers.<Wallet>lambdaQuery().in(Wallet::getUserId, userIds))
                .stream().collect(Collectors.toMap(Wallet::getUserId, w -> w, (a, b) -> a));
        return users.stream()
                .map(u -> toAccountRow(u, walletByUser.get(u.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countAccounts(String phoneKeyword, String userIdKeyword) {
        return userMapper.selectCount(buildUserQuery(phoneKeyword, userIdKeyword));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> listTransactions(
            Long userId, String sourceType, String direction, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = clampSize(size);
        LambdaQueryWrapper<WalletTransaction> q = Wrappers.<WalletTransaction>lambdaQuery()
                .eq(userId != null, WalletTransaction::getUserId, userId)
                .eq(sourceType != null && !sourceType.isBlank(), WalletTransaction::getSourceType, sourceType)
                .eq(direction != null && !direction.isBlank(), WalletTransaction::getDirection, direction)
                .orderByDesc(WalletTransaction::getCreateTime);
        Page<WalletTransaction> pageParam = new Page<>(safePage, safeSize);
        List<WalletTransaction> records = walletTransactionMapper.selectPage(pageParam, q).getRecords();
        return enrichTransactionsWithUser(records);
    }

    @Override
    @Transactional(readOnly = true)
    public long countTransactions(Long userId, String sourceType, String direction) {
        LambdaQueryWrapper<WalletTransaction> q = Wrappers.<WalletTransaction>lambdaQuery()
                .eq(userId != null, WalletTransaction::getUserId, userId)
                .eq(sourceType != null && !sourceType.isBlank(), WalletTransaction::getSourceType, sourceType)
                .eq(direction != null && !direction.isBlank(), WalletTransaction::getDirection, direction);
        return walletTransactionMapper.selectCount(q);
    }

    // ==================================================================
    // Transactional primitives
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Wallet getOrCreateWallet(Long userId) {
        Wallet existing = baseMapper.selectOne(
                Wrappers.<Wallet>lambdaQuery().eq(Wallet::getUserId, userId));
        if (existing != null) {
            return existing;
        }
        requireUserExists(userId);
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(ZERO);
        wallet.setFrozenAmount(ZERO);
        wallet.setVersion(0);
        try {
            baseMapper.insert(wallet);
        } catch (DuplicateKeyException e) {
            // concurrent first-access race: someone else just created it; reload
            Wallet concurrent = baseMapper.selectOne(
                    Wrappers.<Wallet>lambdaQuery().eq(Wallet::getUserId, userId));
            if (concurrent == null) {
                throw e;
            }
            return concurrent;
        }
        return wallet;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Wallet lockWalletForUpdate(Long userId) {
        // Ensure the wallet row exists before locking (first-time users).
        getOrCreateWallet(userId);
        Wallet locked = baseMapper.selectForUpdate(userId);
        if (locked == null) {
            throw new BusinessException(ErrorCode.WALLET_NOT_FOUND, "钱包账户不存在");
        }
        return locked;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletTransaction deductForPayment(
            Long userId, BigDecimal amount, String relatedOrderType,
            Long relatedOrderId, String idempotencyKey) {
        BigDecimal safeAmount = requirePositiveAmount(amount);
        Wallet wallet = lockWalletForUpdate(userId);
        BigDecimal balanceBefore = normalize(wallet.getBalance());
        if (balanceBefore.compareTo(safeAmount) < 0) {
            throw new BusinessException(
                    ErrorCode.WALLET_BALANCE_INSUFFICIENT,
                    "钱包余额不足：当前 " + balanceBefore + "，需要 " + safeAmount);
        }
        int rows = baseMapper.deductBalance(userId, safeAmount);
        if (rows == 0) {
            // Double-check after conditional UPDATE failed (should not happen post-lock, but guard anyway)
            throw new BusinessException(
                    ErrorCode.WALLET_BALANCE_INSUFFICIENT, "钱包余额不足");
        }
        BigDecimal balanceAfter = balanceBefore.subtract(safeAmount).setScale(2, RoundingMode.HALF_UP);
        return insertLedger(wallet, WalletDirection.DEBIT, WalletSourceType.PAY, safeAmount,
                balanceBefore, balanceAfter, relatedOrderType, relatedOrderId,
                WalletOperatorType.USER, userId, idempotencyKey, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletTransaction refundForCancellation(
            Long userId, BigDecimal amount, String relatedOrderType,
            Long relatedOrderId, String idempotencyKey) {
        BigDecimal safeAmount = requirePositiveAmount(amount);
        Wallet wallet = lockWalletForUpdate(userId);
        BigDecimal balanceBefore = normalize(wallet.getBalance());
        int rows = baseMapper.addBalance(userId, safeAmount);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.WALLET_NOT_FOUND, "钱包账户不存在");
        }
        BigDecimal balanceAfter = balanceBefore.add(safeAmount).setScale(2, RoundingMode.HALF_UP);
        // Refund is a system-initiated action tied to the original order's user.
        return insertLedger(wallet, WalletDirection.CREDIT, WalletSourceType.REFUND, safeAmount,
                balanceBefore, balanceAfter, relatedOrderType, relatedOrderId,
                WalletOperatorType.SYSTEM, null, idempotencyKey, "订单/预约取消自动退款");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Wallet rechargeByAdmin(Long userId, BigDecimal amount, String reason, Long operatorId) {
        requireReason(reason);
        BigDecimal safeAmount = requirePositiveAmount(amount);
        Wallet wallet = lockWalletForUpdate(userId);
        BigDecimal balanceBefore = normalize(wallet.getBalance());
        baseMapper.addBalance(userId, safeAmount);
        BigDecimal balanceAfter = balanceBefore.add(safeAmount).setScale(2, RoundingMode.HALF_UP);
        insertLedger(wallet, WalletDirection.CREDIT, WalletSourceType.RECHARGE, safeAmount,
                balanceBefore, balanceAfter, null, null,
                WalletOperatorType.ADMIN, operatorId, null, reason);
        return baseMapper.selectById(wallet.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Wallet adjustByAdmin(Long userId, BigDecimal amount, String direction,
                                 String reason, Long operatorId) {
        requireReason(reason);
        BigDecimal safeAmount = requirePositiveAmount(amount);
        WalletDirection dir = parseDirection(direction);
        Wallet wallet = lockWalletForUpdate(userId);
        BigDecimal balanceBefore = normalize(wallet.getBalance());
        BigDecimal balanceAfter;
        if (dir == WalletDirection.CREDIT) {
            baseMapper.addBalance(userId, safeAmount);
            balanceAfter = balanceBefore.add(safeAmount).setScale(2, RoundingMode.HALF_UP);
        } else {
            if (balanceBefore.compareTo(safeAmount) < 0) {
                throw new BusinessException(
                        ErrorCode.WALLET_BALANCE_INSUFFICIENT,
                        "钱包余额不足：当前 " + balanceBefore + "，需扣减 " + safeAmount);
            }
            int rows = baseMapper.deductBalance(userId, safeAmount);
            if (rows == 0) {
                throw new BusinessException(ErrorCode.WALLET_BALANCE_INSUFFICIENT, "钱包余额不足");
            }
            balanceAfter = balanceBefore.subtract(safeAmount).setScale(2, RoundingMode.HALF_UP);
        }
        insertLedger(wallet, dir, WalletSourceType.ADMIN_ADJUST, safeAmount,
                balanceBefore, balanceAfter, null, null,
                WalletOperatorType.ADMIN, operatorId, null, reason);
        return baseMapper.selectById(wallet.getId());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTransaction(Consumer<WalletTransaction> builder) {
        WalletTransaction tx = new WalletTransaction();
        builder.accept(tx);
        if (tx.getUserId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "钱包流水必须指定 user_id");
        }
        try {
            walletTransactionMapper.insert(tx);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    ErrorCode.WALLET_TRANSACTION_DUPLICATE, "重复的钱包操作");
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private WalletTransaction insertLedger(
            Wallet wallet, WalletDirection direction, WalletSourceType sourceType,
            BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
            String relatedOrderType, Long relatedOrderId,
            WalletOperatorType operatorType, Long operatorId,
            String idempotencyKey, String reason) {
        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(wallet.getUserId());
        tx.setDirection(direction.getCode());
        tx.setSourceType(sourceType.getCode());
        tx.setAmount(amount);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setRelatedOrderType(relatedOrderType);
        tx.setRelatedOrderId(relatedOrderId);
        tx.setOperatorType(operatorType.getCode());
        tx.setOperatorId(operatorId);
        tx.setIdempotencyKey(idempotencyKey);
        tx.setReason(reason);
        try {
            walletTransactionMapper.insert(tx);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    ErrorCode.WALLET_TRANSACTION_DUPLICATE, "重复的钱包操作");
        }
        return tx;
    }

    private Wallet getOrCreateWalletReadOnly(Long userId) {
        // Retained for potential future read paths that need the entity (not the DTO).
        return baseMapper.selectOne(
                Wrappers.<Wallet>lambdaQuery().eq(Wallet::getUserId, userId));
    }

    private void requireUserExists(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
    }

    private BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.WALLET_AMOUNT_INVALID, "金额必须大于 0");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                    ErrorCode.WALLET_ADJUST_REASON_REQUIRED, "钱包调整必须填写理由");
        }
        if (reason.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "理由长度不能超过 500 字符");
        }
    }

    private WalletDirection parseDirection(String direction) {
        if (direction == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少调整方向");
        }
        return Optional.ofNullable(WalletDirection.getByCode(direction.toUpperCase()))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VALIDATION_ERROR, "无效的调整方向：" + direction));
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    /**
     * Resolves user IDs matching the phone keyword (LIKE '%keyword%').
     * Returns null when no keyword is given (meaning "all users").
     * Returns an empty singleton list when a keyword is given but no user matches,
     * so the downstream {@code in()} clause yields zero rows rather than all rows.
     */
    /**
     * User-table query for the account list: optional phone LIKE and/or userId LIKE filters,
     * excludes soft-deleted users, ordered by newest user first. Used by both {@link #listAccounts}
     * and {@link #countAccounts} so paging and count stay consistent.
     *
     * <p>userId is a BIGINT snowflake column. To support partial matching (e.g. admin remembers
     * only the first few digits) we CAST it to CHAR and apply LIKE. Both filters, when present,
     * are AND-combined.</p>
     */
    private LambdaQueryWrapper<User> buildUserQuery(String phoneKeyword, String userIdKeyword) {
        LambdaQueryWrapper<User> q = Wrappers.<User>lambdaQuery()
                .orderByDesc(User::getCreateTime);
        if (phoneKeyword != null && !phoneKeyword.isBlank()) {
            q.like(User::getPhone, phoneKeyword.trim());
        }
        if (userIdKeyword != null && !userIdKeyword.isBlank()) {
            // Snowflake IDs are 18-19 digit numbers; CAST(id AS CHAR) LIKE '%2076%' lets admins
            // find users by a remembered prefix/fragment. Trims whitespace from the input.
            String pattern = userIdKeyword.trim();
            q.apply("CAST(id AS CHAR) LIKE {0}", "%" + pattern + "%");
        }
        return q;
    }

    /**
     * Builds an account row from a User, left-joining its wallet if one exists.
     * Users without a wallet show zero balance and null walletId/createTime/updateTime —
     * they are still rechargeable (the recharge flow lazy-creates the wallet).
     */
    private WalletAccountRow toAccountRow(User user, Wallet wallet) {
        if (wallet == null) {
            return new WalletAccountRow(
                    null, user.getId(), user.getNickname(), user.getPhone(),
                    ZERO, ZERO, 0, null, null);
        }
        return new WalletAccountRow(
                wallet.getId(), user.getId(), user.getNickname(), user.getPhone(),
                normalize(wallet.getBalance()), normalize(wallet.getFrozenAmount()),
                wallet.getVersion(), wallet.getCreateTime(), wallet.getUpdateTime());
    }

    private WalletResponse toWalletResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(), wallet.getUserId(),
                normalize(wallet.getBalance()), normalize(wallet.getFrozenAmount()),
                wallet.getUpdateTime());
    }

    /**
     * Batch-loads user nickname/phone for a page of ledger rows so the admin list can show
     * "who was paid" without N+1 queries. Users that no longer exist (deleted) get null fields.
     */
    private List<WalletTransactionResponse> enrichTransactionsWithUser(List<WalletTransaction> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = records.stream().map(WalletTransaction::getUserId).distinct().toList();
        Map<Long, User> userById = userMapper.selectList(
                Wrappers.<User>lambdaQuery().in(User::getId, userIds))
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        return records.stream().map(tx -> {
            User u = userById.get(tx.getUserId());
            return new WalletTransactionResponse(
                    tx.getId(), tx.getUserId(),
                    u != null ? u.getNickname() : null,
                    u != null ? u.getPhone() : null,
                    tx.getDirection(), tx.getSourceType(), tx.getAmount(),
                    tx.getBalanceBefore(), tx.getBalanceAfter(),
                    tx.getRelatedOrderType(), tx.getRelatedOrderId(),
                    tx.getOperatorType(), tx.getOperatorId(), tx.getReason(),
                    tx.getCreateTime());
        }).toList();
    }
}
