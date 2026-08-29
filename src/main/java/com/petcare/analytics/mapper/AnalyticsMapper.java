package com.petcare.analytics.mapper;

import com.petcare.analytics.dto.DailyRevenueStat;
import com.petcare.analytics.dto.OverviewCountRow;
import com.petcare.analytics.dto.TopItemStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 运营统计只读聚合查询。
 * <p>
 * 刻意单条 GROUP BY 出全窗口数据（苍穹外卖的报表是逐日循环查库的 N+1，这里不抄）。
 * 时间轴口径沿用既有代码：商品订单按 create_time，预约按 booking_date；
 * 有效口径统一 status='COMPLETED' AND deleted=0。SQL 同时兼容 MySQL 8 与 H2(MySQL 模式)。
 */
@Mapper
public interface AnalyticsMapper {

    @Select("""
            SELECT CAST(create_time AS DATE) AS stat_date,
                   COUNT(*) AS order_count,
                   COALESCE(SUM(total_amount), 0) AS amount
            FROM product_order
            WHERE status = 'COMPLETED' AND deleted = 0
              AND create_time >= #{start} AND create_time < #{endExclusive}
            GROUP BY CAST(create_time AS DATE)
            ORDER BY stat_date
            """)
    List<DailyRevenueStat> sumProductRevenueByDay(@Param("start") LocalDateTime start,
                                                  @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT booking_date AS stat_date,
                   COUNT(*) AS order_count,
                   COALESCE(SUM(price), 0) AS amount
            FROM service_booking
            WHERE status = 'COMPLETED' AND deleted = 0
              AND booking_date >= #{start} AND booking_date <= #{end}
            GROUP BY booking_date
            ORDER BY stat_date
            """)
    List<DailyRevenueStat> sumBookingRevenueByDay(@Param("start") LocalDate start,
                                                  @Param("end") LocalDate end);

    @Select("""
            SELECT COUNT(*) AS total_count,
                   COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed_count,
                   COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN total_amount ELSE 0 END), 0) AS completed_amount
            FROM product_order
            WHERE deleted = 0
              AND create_time >= #{start} AND create_time < #{endExclusive}
            """)
    OverviewCountRow summarizeProductOrders(@Param("start") LocalDateTime start,
                                            @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT COUNT(*) AS total_count,
                   COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed_count,
                   COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN price ELSE 0 END), 0) AS completed_amount
            FROM service_booking
            WHERE deleted = 0
              AND booking_date >= #{start} AND booking_date <= #{end}
            """)
    OverviewCountRow summarizeBookings(@Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    @Select("""
            SELECT oi.product_id AS item_id,
                   oi.product_name AS item_name,
                   SUM(oi.quantity) AS quantity,
                   SUM(oi.total_amount) AS amount
            FROM product_order_item oi
            JOIN product_order o ON o.id = oi.order_id
            WHERE o.status = 'COMPLETED' AND o.deleted = 0
              AND o.create_time >= #{start} AND o.create_time < #{endExclusive}
            GROUP BY oi.product_id, oi.product_name
            ORDER BY quantity DESC, amount DESC
            LIMIT #{limit}
            """)
    List<TopItemStat> topSellingProducts(@Param("start") LocalDateTime start,
                                         @Param("endExclusive") LocalDateTime endExclusive,
                                         @Param("limit") int limit);

    @Select("""
            SELECT sb.service_item_id AS item_id,
                   si.name AS item_name,
                   COUNT(*) AS quantity,
                   COALESCE(SUM(sb.price), 0) AS amount
            FROM service_booking sb
            LEFT JOIN service_item si ON si.id = sb.service_item_id
            WHERE sb.status = 'COMPLETED' AND sb.deleted = 0
              AND sb.booking_date >= #{start} AND sb.booking_date <= #{end}
            GROUP BY sb.service_item_id, si.name
            ORDER BY quantity DESC, amount DESC
            LIMIT #{limit}
            """)
    List<TopItemStat> topSellingServices(@Param("start") LocalDate start,
                                         @Param("end") LocalDate end,
                                         @Param("limit") int limit);
}
