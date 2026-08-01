package com.petcare.wallet.it;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.petcare.common.persistence.AbstractTcMySqlIT;
import com.petcare.user.entity.User;
import com.petcare.user.mapper.UserMapper;
import com.petcare.wallet.entity.Wallet;
import com.petcare.wallet.entity.WalletTransaction;
import com.petcare.wallet.mapper.WalletMapper;
import com.petcare.wallet.mapper.WalletTransactionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MySQL integration test verifying that {@link Wallet} and {@link WalletTransaction} entities map
 * correctly to the {@code user_wallet} and {@code wallet_transaction} schema columns.
 *
 * <p>Follows the same pattern as {@code MySqlMapperIntegrationIT}: insert via MyBatis-Plus,
 * re-read, and assert every persisted column. Also verifies the unique constraint
 * {@code uk_user_id} and {@code uk_wallet_idempotency} are enforced by MySQL.</p>
 *
 * <p>Requires real MySQL 8: {@code mvn test -Ptc-mysql}.</p>
 */
@Tag("tc-mysql")
@Transactional
class WalletEntityMappingIT extends AbstractTcMySqlIT {

    @Autowired
    private WalletMapper walletMapper;

    @Autowired
    private WalletTransactionMapper walletTransactionMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("Wallet entity maps to all user_wallet columns and persists balance with scale 2")
    void walletPersistsAllColumns() {
        User user = createUser();

        Wallet wallet = new Wallet();
        wallet.setUserId(user.getId());
        wallet.setBalance(new BigDecimal("123.45"));
        wallet.setFrozenAmount(new BigDecimal("0.00"));
        wallet.setVersion(0);
        walletMapper.insert(wallet);

        Wallet reloaded = walletMapper.selectById(wallet.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getUserId()).isEqualTo(user.getId());
        assertThat(reloaded.getBalance()).isEqualByComparingTo("123.45");
        assertThat(reloaded.getBalance().scale()).isEqualTo(2);
        assertThat(reloaded.getFrozenAmount()).isEqualByComparingTo("0.00");
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreateTime()).isNotNull();
        assertThat(reloaded.getUpdateTime()).isNotNull();
        assertThat(reloaded.getDeleted()).isZero();
    }

    @Test
    @DisplayName("WalletTransaction entity maps to all wallet_transaction columns")
    void transactionPersistsAllColumns() {
        User user = createUser();
        Wallet wallet = createWallet(user.getId(), "100.00");

        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(user.getId());
        tx.setDirection("DEBIT");
        tx.setSourceType("PAY");
        tx.setAmount(new BigDecimal("30.00"));
        tx.setBalanceBefore(new BigDecimal("100.00"));
        tx.setBalanceAfter(new BigDecimal("70.00"));
        tx.setRelatedOrderType("PRODUCT_ORDER");
        tx.setRelatedOrderId(999L);
        tx.setOperatorType("USER");
        tx.setOperatorId(user.getId());
        tx.setIdempotencyKey("idem-mapping-" + System.nanoTime());
        tx.setReason("test payment");
        walletTransactionMapper.insert(tx);

        WalletTransaction reloaded = walletTransactionMapper.selectById(tx.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getUserId()).isEqualTo(user.getId());
        assertThat(reloaded.getDirection()).isEqualTo("DEBIT");
        assertThat(reloaded.getSourceType()).isEqualTo("PAY");
        assertThat(reloaded.getAmount()).isEqualByComparingTo("30.00");
        assertThat(reloaded.getBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(reloaded.getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(reloaded.getRelatedOrderType()).isEqualTo("PRODUCT_ORDER");
        assertThat(reloaded.getRelatedOrderId()).isEqualTo(999L);
        assertThat(reloaded.getOperatorType()).isEqualTo("USER");
        assertThat(reloaded.getOperatorId()).isEqualTo(user.getId());
        assertThat(reloaded.getReason()).isEqualTo("test payment");
        assertThat(reloaded.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("uk_user_id prevents two wallets for the same user")
    void uniqueUserIdConstraint() {
        User user = createUser();
        createWallet(user.getId(), "0.00");

        Wallet duplicate = new Wallet();
        duplicate.setUserId(user.getId());
        duplicate.setBalance(new BigDecimal("0.00"));
        duplicate.setFrozenAmount(new BigDecimal("0.00"));
        duplicate.setVersion(0);

        assertThatThrownBy(() -> walletMapper.insert(duplicate))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("uk_wallet_idempotency prevents duplicate ledger entries for same (user, key)")
    void uniqueIdempotencyConstraint() {
        User user = createUser();
        createWallet(user.getId(), "0.00");
        String idemKey = "idem-uniq-" + System.nanoTime();

        insertLedger(user.getId(), idemKey, "50.00", "100.00", "50.00");

        WalletTransaction dup = new WalletTransaction();
        dup.setUserId(user.getId());
        dup.setDirection("DEBIT");
        dup.setSourceType("PAY");
        dup.setAmount(new BigDecimal("50.00"));
        dup.setBalanceBefore(new BigDecimal("100.00"));
        dup.setBalanceAfter(new BigDecimal("50.00"));
        dup.setOperatorType("USER");
        dup.setOperatorId(user.getId());
        dup.setIdempotencyKey(idemKey);

        assertThatThrownBy(() -> walletTransactionMapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("selectForUpdate locks the row and returns current state")
    void selectForUpdateReturnsRow() {
        User user = createUser();
        createWallet(user.getId(), "200.00");

        Wallet locked = walletMapper.selectForUpdate(user.getId());
        assertThat(locked).isNotNull();
        assertThat(locked.getBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("deductBalance conditional UPDATE only succeeds when balance >= amount")
    void deductBalanceConditionalUpdate() {
        User user = createUser();
        createWallet(user.getId(), "50.00");

        // Successful deduction: balance 50 >= 30
        int rows = walletMapper.deductBalance(user.getId(), new BigDecimal("30.00"));
        assertThat(rows).isEqualTo(1);
        assertThat(walletMapper.selectOne(
                Wrappers.<Wallet>lambdaQuery().eq(Wallet::getUserId, user.getId())).getBalance())
                .isEqualByComparingTo("20.00");

        // Failed deduction: balance 20 < 30
        int rows2 = walletMapper.deductBalance(user.getId(), new BigDecimal("30.00"));
        assertThat(rows2).isZero();
    }

    private User createUser() {
        User user = new User();
        user.setPhone("139" + System.nanoTime() % 100000000L);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return user;
    }

    private Wallet createWallet(Long userId, String balance) {
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(new BigDecimal(balance));
        wallet.setFrozenAmount(new BigDecimal("0.00"));
        wallet.setVersion(0);
        walletMapper.insert(wallet);
        return wallet;
    }

    private void insertLedger(Long userId, String idemKey, String amount,
                              String before, String after) {
        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(userId);
        tx.setDirection("DEBIT");
        tx.setSourceType("PAY");
        tx.setAmount(new BigDecimal(amount));
        tx.setBalanceBefore(new BigDecimal(before));
        tx.setBalanceAfter(new BigDecimal(after));
        tx.setOperatorType("USER");
        tx.setOperatorId(userId);
        tx.setIdempotencyKey(idemKey);
        walletTransactionMapper.insert(tx);
    }
}
