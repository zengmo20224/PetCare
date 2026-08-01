package com.petcare.wallet.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petcare.wallet.entity.WalletTransaction;

/**
 * MyBatis-Plus {@link IService} for {@link WalletTransaction}.
 *
 * <p>The ledger is append-only: callers must never use {@link IService#updateById} or
 * {@code removeById} on ledger rows. Only read-side methods ({@link IService#list},
 * {@link IService#getOne}, {@link IService#count}, {@link IService#save}) are meaningful.
 * Architectural guard {@code MapperAndServiceCoverageTest} requires every {@code @TableName}
 * entity to have a registered {@link IService} bean.</p>
 */
public interface WalletTransactionService extends IService<WalletTransaction> {
}
