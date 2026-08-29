package com.petcare.analytics.dto;

import java.util.List;

/**
 * 销量排行报表：服务与商品两个榜单。
 */
public record TopItemsReport(
        List<TopItemStat> services,
        List<TopItemStat> products
) {
}
