package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.booking.service.BookingApplicationService;

/**
 * 客服只读 Tool：查当前用户的预约状态（对标 docs/09 §5.1，用户身份 Tool）。
 * <p>
 * <b>越权防护（B7 + T3）</b>：复用 {@link BookingApplicationService#getBooking(Long, Long)}
 * 的归属校验——该方法越权时返回 NOT_FOUND（更安全，不暴露存在性）。
 */
public class GetMyBookingStatusTool implements AgentTool {

    public static final String NAME = "getMyBookingStatus";
    private static final String ARG_BOOKING_ID = "bookingId";

    private final BookingApplicationService bookingService;

    public GetMyBookingStatusTool(BookingApplicationService bookingService) {
        this.bookingService = bookingService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "按预约 ID 查询【当前用户自己】的预约状态、时间和价格。用户问'我的预约确认了吗'时调用，需用户提供预约号。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
        ctx.requireUser();
        Long bookingId = input.getAsLong(ARG_BOOKING_ID);
        if (bookingId == null) {
            return AgentToolResult.fail("缺少有效的预约号");
        }
        try {
            BookingResponse b = bookingService.getBooking(ctx.currentUserId(), bookingId);
            if (b == null) {
                return AgentToolResult.fail("未找到相关预约");
            }
            String maskedPhone = maskPhone(b.contactPhone());
            String summary = String.format(
                    "预约 %s（服务：%s，状态：%s），日期 %s 时间 %s-%s，售价 %s 元，联系电话：%s。",
                    b.bookingNo() == null ? String.valueOf(bookingId) : b.bookingNo(),
                    b.serviceItemName() == null ? "未知服务" : b.serviceItemName(),
                    b.status() == null ? "未知" : b.status(),
                    b.bookingDate() == null ? "未知" : b.bookingDate(),
                    b.startTime() == null ? "未知" : b.startTime(),
                    b.endTime() == null ? "未知" : b.endTime(),
                    b.price() == null ? "未知" : b.price().toPlainString(),
                    maskedPhone);
            return AgentToolResult.ok(summary);
        } catch (Exception e) {
            return AgentToolResult.fail("未找到相关预约");
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "未提供";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
