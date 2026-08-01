package com.petcare.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petcare.wallet.entity.WalletTransaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WalletTransactionMapper extends BaseMapper<WalletTransaction> {
}
