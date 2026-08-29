package com.petcare.analytics.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 销量排行条目（服务或商品）。服务条目的 itemName 来自 service_item 联查，
 * 商品条目来自订单项名称快照（商品改名/删除不影响历史榜单）。
 */
@Getter
@Setter
public class TopItemStat {

    private Long itemId;

    private String itemName;

    private Long quantity;

    private BigDecimal amount;
}
