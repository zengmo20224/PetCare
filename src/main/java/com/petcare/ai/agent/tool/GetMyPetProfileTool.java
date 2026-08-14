package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.user.dto.PetResponse;
import com.petcare.user.service.PetApplicationService;

import java.util.List;

/**
 * 社区助手只读 Tool：查当前用户的宠物档案（对标 docs/09 §5.3.1）。
 * <p>
 * 用于发帖助手个性化草稿（品种/年龄等事实素材）。依赖 {@link PetApplicationService}
 * （业务只读门面），不依赖 Mapper（B1/B7 守卫覆盖）。
 * 归属校验：只按 ctx.currentUserId 查询，天然无越权面。
 */
public class GetMyPetProfileTool implements AgentTool {

    public static final String NAME = "getMyPetProfile";

    private final PetApplicationService petApplicationService;

    public GetMyPetProfileTool(PetApplicationService petApplicationService) {
        this.petApplicationService = petApplicationService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "查询当前用户登记的宠物档案（名称/品种/类型/体型等），用于生成个性化帖子草稿。无参数。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
        ctx.requireUser();
        try {
            List<PetResponse> pets = petApplicationService.listCurrentUserPets(ctx.currentUserId());
            if (pets == null || pets.isEmpty()) {
                return AgentToolResult.ok("当前用户未登记宠物档案。");
            }
            StringBuilder sb = new StringBuilder("当前用户的宠物档案（").append(pets.size()).append(" 只）：");
            for (PetResponse p : pets) {
                sb.append("\n- 名称：").append(safe(p.name()));
                if (p.type() != null) {
                    sb.append("，类型：").append(p.type());
                }
                if (p.breed() != null && !p.breed().isBlank()) {
                    sb.append("，品种：").append(p.breed());
                }
                if (p.size() != null && !p.size().isBlank()) {
                    sb.append("，体型：").append(p.size());
                }
            }
            return AgentToolResult.ok(sb.toString());
        } catch (Exception e) {
            return AgentToolResult.fail("宠物档案暂时无法获取");
        }
    }

    private static String safe(String s) {
        return s == null ? "未命名" : s;
    }
}
