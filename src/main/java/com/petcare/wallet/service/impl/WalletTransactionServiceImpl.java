package com.petcare.wallet.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petcare.wallet.entity.WalletTransaction;
import com.petcare.wallet.mapper.WalletTransactionMapper;
import com.petcare.wallet.service.WalletTransactionService;
import org.springframework.stereotype.Service;

/**
 * Default {@link WalletTransactionService} implementation backed by MyBatis-Plus.
 * Only used for read queries; ledger rows are never updated or deleted (see interface contract).
 */
@Service
public class WalletTransactionServiceImpl
        extends ServiceImpl<WalletTransactionMapper, WalletTransaction>
        implements WalletTransactionService {
}
