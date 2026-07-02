from __future__ import annotations

from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Cm, Pt


ROOT = Path(r"F:/trae code/petcare-o2o")
DOCS = Path(r"E:/27269/Documents")
EVIDENCE = ROOT / "docs" / "scm-assignment-evidence"

BUILD_TEMPLATE = DOCS / "XXX项目构建和部署指南.docx"
CM_TEMPLATE = DOCS / "项目软件配置管理计划摸板20260609.docx"
REPORT_TEMPLATE = DOCS / "课程设计报告模版20260610.docx"

OUT_BUILD = DOCS / "PetCare O2O项目构建和部署指南.docx"
OUT_CM = DOCS / "PetCare O2O项目软件配置管理计划.docx"
OUT_REPORT = DOCS / "PetCare O2O软件配置管理课程设计报告.docx"

MEMBERS = [
    ("2320160033", "曾志洪", "1.1", "配置管理计划、CI/CD、Docker 部署、证据截图、文档整合"),
    ("2320160030", "郑蔚然", "1.0", "用户端 H5、管理端页面、前端构建与联调验证"),
    ("", "刘意沁", "0.9", "后端接口测试、文档校对、配置项核对"),
    ("", "陈灏言", "1.0", "数据库脚本、部署验证、变更记录整理"),
]


def clear_document(doc: Document) -> None:
    body = doc._body._element
    for child in list(body):
        if child.tag.endswith("}sectPr"):
            continue
        body.remove(child)


def set_run_font(run, size: int | None = None, bold: bool | None = None) -> None:
    run.font.name = "宋体"
    run._element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")
    if size:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold


def normalize_doc(doc: Document) -> None:
    for section in doc.sections:
        section.top_margin = Cm(2.54)
        section.bottom_margin = Cm(2.54)
        section.left_margin = Cm(2.7)
        section.right_margin = Cm(2.7)
    styles = doc.styles
    for name in ["Normal", "Body Text"]:
        if name in styles:
            style = styles[name]
            style.font.name = "宋体"
            style._element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")
            style.font.size = Pt(10.5)
    for name in ["Heading 1", "Heading 2", "Heading 3"]:
        if name in styles:
            style = styles[name]
            style.font.name = "黑体"
            style._element.rPr.rFonts.set(qn("w:eastAsia"), "黑体")


def para(doc: Document, text: str = "", style: str | None = None, bold: bool = False, align=None):
    p = doc.add_paragraph(style=style) if style else doc.add_paragraph()
    if text:
        r = p.add_run(text)
        set_run_font(r, bold=bold)
    if align is not None:
        p.alignment = align
    return p


def heading(doc: Document, text: str, level: int = 1):
    p = doc.add_heading(text, level=level)
    for run in p.runs:
        set_run_font(run, bold=True)
    return p


def bullet(doc: Document, text: str):
    p = doc.add_paragraph(style="List Bullet")
    r = p.add_run(text)
    set_run_font(r)
    return p


def numbered(doc: Document, text: str):
    p = doc.add_paragraph(style="List Number")
    r = p.add_run(text)
    set_run_font(r)
    return p


def table(doc: Document, rows: list[list[str]], style: str = "Table Grid"):
    t = doc.add_table(rows=len(rows), cols=len(rows[0]))
    t.style = style
    for i, row in enumerate(rows):
        for j, value in enumerate(row):
            cell = t.cell(i, j)
            cell.text = ""
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER if i == 0 or len(value) < 16 else WD_ALIGN_PARAGRAPH.LEFT
            r = p.add_run(value)
            set_run_font(r, bold=(i == 0))
    return t


def add_caption(doc: Document, text: str):
    p = para(doc, text)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for r in p.runs:
        r.italic = True
        r.font.size = Pt(9)


def image(doc: Document, file: str, caption: str, width_cm: float = 15.6):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run()
    run.add_picture(str(EVIDENCE / file), width=Cm(width_cm))
    add_caption(doc, caption)


def contribution_table(doc: Document, include_work: bool = False):
    if include_work:
        rows = [["学号", "姓名", "文档贡献度\n（均值为1）", "开发与持续集成工作", "课程成绩"]]
        rows += [[sid, name, coef, work, ""] for sid, name, coef, work in MEMBERS]
    else:
        rows = [["学号", "姓名", "文档贡献度\n（均值为1）", "作业成绩"]]
        rows += [[sid, name, coef, ""] for sid, name, coef, _ in MEMBERS]
    return table(doc, rows)


def cover(doc: Document, title: str, subtitle: str | None = None, report: bool = False):
    for _ in range(3):
        para(doc)
    if report:
        p = para(doc, "工学院", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER)
        p.runs[0].font.size = Pt(22)
        para(doc)
        para(doc, "课程名称：软件配置管理", align=WD_ALIGN_PARAGRAPH.CENTER)
        para(doc, "项目名称：PetCare O2O 宠物门店 O2O 服务平台", align=WD_ALIGN_PARAGRAPH.CENTER)
        para(doc, "项目组成员及分工及成绩评定", align=WD_ALIGN_PARAGRAPH.CENTER)
        para(doc, "配置管理计划文档(30)+个人开发与配置管理计划实施(30)+Jenkins持续集成(30)+演示(10)", align=WD_ALIGN_PARAGRAPH.CENTER)
        contribution_table(doc, include_work=True)
        para(doc, "2026 年 6 月 25 日", align=WD_ALIGN_PARAGRAPH.CENTER)
        doc.add_page_break()
        return
    p = para(doc, title, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER)
    p.runs[0].font.size = Pt(20)
    if subtitle:
        para(doc, subtitle, align=WD_ALIGN_PARAGRAPH.CENTER)
    para(doc)
    contribution_table(doc)
    doc.add_page_break()


def build_guide():
    doc = Document(str(BUILD_TEMPLATE))
    clear_document(doc)
    normalize_doc(doc)
    cover(doc, "PetCare O2O项目构建和部署指南", "软件配置管理课程作业")

    heading(doc, "实验目的", 1)
    for item in [
        "掌握使用 Git 对 PetCare O2O 项目进行版本控制、基线标识和提交日志追踪的方法。",
        "掌握使用 Maven 构建 Spring Boot 后端、使用 npm/Vite/uni-app 构建前端工程的方法。",
        "掌握使用 Docker Compose 部署 MySQL、后端 API、管理端 Web、用户端 H5 四个服务的方法。",
        "掌握使用 Jenkins 和 GitHub Actions 落实自动编译、测试、打包、部署和报告归档的持续集成流程。",
    ]:
        bullet(doc, item)

    heading(doc, "实验环境要求", 1)
    table(doc, [
        ["工具", "本机验证版本/状态", "用途"],
        ["JDK", "Java 21.0.9 / Maven 使用 JDK 21.0.7", "后端编译与运行"],
        ["Maven", "3.9.11", "后端构建、测试、打包"],
        ["Node.js / npm", "Node 24.12.0 / npm 11.6.2", "管理端与 H5 构建"],
        ["Git", "2.53.0.windows.2", "版本库、分支、tag、日志"],
        ["Docker Compose", "v5.1.4", "四服务容器编排部署"],
    ])
    image(doc, "01-env-versions.png", "图1 构建环境版本验证截图")

    heading(doc, "一、构建步骤", 1)
    heading(doc, "步骤 1：克隆或打开项目代码", 2)
    para(doc, "项目仓库路径为 https://github.com/zengmo20224/-------2-.git。本次作业在本机目录 F:\\trae code\\petcare-o2o 中完成，当前分支为 main，远程仓库为 origin。")
    image(doc, "02-git-baseline-tags.png", "图2 Git 远程仓库、分支与基线 tag 验证截图")

    heading(doc, "步骤 2：检查提交日志与工作区", 2)
    para(doc, "构建前检查最近提交和工作区状态，确认项目提交可追溯，提交信息基本遵循 Conventional Commits 规范。")
    image(doc, "03-git-log-status.png", "图3 最近提交日志与工作区状态截图")

    heading(doc, "步骤 3：构建后端 Spring Boot 工程", 2)
    para(doc, "在项目根目录执行 mvn -B -ntp package -DskipTests。实际验证中命令执行成功，生成 target/petcare-o2o-api-0.1.0-SNAPSHOT.jar。")
    para(doc, "说明：该命令跳过测试，仅验证编译与打包；完整测试和覆盖率证据见课程报告与 Jenkins 历史证据。")

    heading(doc, "步骤 4：构建管理端 PC Web", 2)
    para(doc, "进入 frontend/admin-web 后执行 npm run build，构建产物输出到 frontend/admin-web/dist。实际验证中构建成功，存在 Vite/Rolldown 注释和大 chunk 警告，不影响 dist 生成。")

    heading(doc, "步骤 5：构建用户端 H5", 2)
    para(doc, "进入 frontend/miniapp 后执行 npm run build:h5，构建产物输出到 frontend/miniapp/dist/build/h5。实际验证中 uni-app 编译完成。")
    image(doc, "04-build-artifacts.png", "图4 后端 jar、管理端 dist、H5 dist 构建产物截图")

    heading(doc, "二、部署步骤", 1)
    heading(doc, "步骤 1：准备环境变量", 2)
    para(doc, "复制 .env.example 为 .env，并设置 MYSQL_ROOT_PASSWORD、DB_USERNAME、DB_PASSWORD、JWT_SECRET、IMAGE_TAG 等参数。项目已通过 .gitignore 排除 .env，避免敏感信息进入版本库。")

    heading(doc, "步骤 2：使用 Docker Compose 一键部署", 2)
    para(doc, "在项目根目录执行 docker compose up -d --build --wait。Compose 文件定义 mysql、api、admin-web、h5 四个服务，并配置健康检查和端口映射。")
    table(doc, [
        ["服务", "访问地址/端口", "说明"],
        ["admin-web", "http://localhost:8080", "管理端 PC Web，nginx 托管并反向代理 API"],
        ["h5", "http://localhost:8081", "用户端响应式 H5，nginx 托管"],
        ["api", "http://localhost:8082/api/v1/system/health", "Spring Boot 后端 API 健康检查"],
        ["mysql", "127.0.0.1:3317 -> 3306", "MySQL 8.0 数据库容器"],
    ])
    image(doc, "05-docker-compose-health.png", "图5 Docker Compose 四服务 healthy 与 HTTP 健康检查截图")

    heading(doc, "步骤 3：浏览器访问验证", 2)
    para(doc, "部署成功后访问管理端登录页、用户端 H5 首页和 API 健康端点，验证前端、后端和数据库链路正常。")
    image(doc, "10-admin-web-page.png", "图6 管理端 PetCare Admin 登录页截图")
    image(doc, "11-h5-page.png", "图7 用户端 H5 首页截图", width_cm=7.0)
    image(doc, "12-api-health-page.png", "图8 API 健康端点截图", width_cm=13.0)

    heading(doc, "步骤 4：持续集成部署说明", 2)
    para(doc, "Jenkinsfile 定义 Checkout、Backend Build、Backend Test、Backend Package、Docker Build、Deploy、Health Check 等阶段；.github/workflows 中保留 GitHub Actions CI 与 tag 部署流水线。")
    image(doc, "08-ci-pipeline-files.png", "图9 Jenkinsfile 与 GitHub Actions 流水线配置截图")

    heading(doc, "三、常见问题与处理", 1)
    table(doc, [
        ["问题", "原因", "处理方式"],
        ["前端首次构建提示 vue-tsc 找不到", "子项目依赖未安装", "进入对应前端目录执行 npm install 后重试"],
        ["npm audit 有 high/moderate 漏洞", "前端依赖链存在安全公告", "作为后续整改项，评估 npm audit fix 的兼容性后处理"],
        ["端口冲突", "本机已有 MySQL 或其他项目占用端口", "本项目当前 MySQL 映射为 3317，避免与 3307/3306 冲突"],
        ["API 健康检查失败", "后端未就绪或 JWT/数据库配置错误", "查看 docker compose logs api 与 .env 配置"],
    ])

    doc.save(str(OUT_BUILD))


def cm_plan():
    doc = Document(str(CM_TEMPLATE))
    clear_document(doc)
    normalize_doc(doc)
    cover(doc, "PetCare O2O项目软件配置管理计划", "版本：V1.0    日期：2026.06.25")

    para(doc, "文档修订信息", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER)
    table(doc, [
        ["修改人", "修改内容", "修改原因", "版本信息"],
        ["曾志洪", "完成 PetCare O2O 配置管理计划初稿", "课程期末作业要求", "V1.0"],
        ["配置管理组", "补充基线、变更、CI/CD、部署和审计证据", "保证配置管理计划可落地执行", "V1.1"],
    ])
    doc.add_page_break()

    heading(doc, "1、概述", 1)
    heading(doc, "1.1 项目概述", 2)
    para(doc, "PetCare O2O 是面向单体宠物门店的模块化单体应用，覆盖宠物服务预约、商品零售、社区互动和营销活动四大业务域。系统采用 Spring Boot 3.3 + MyBatis-Plus + MySQL 8 后端，管理端采用 Vue 3 + Vite + Element Plus，用户端采用 UniApp + Vue 3 交付响应式 H5。")
    heading(doc, "1.2 目的和范围", 2)
    para(doc, "本文档描述 PetCare O2O 项目的软件配置管理计划，向项目组及受 SCM 活动影响的人员提供版本控制、配置项识别、基线管理、变更控制、配置审计、构建与发布的操作指南。计划适用于项目从需求、开发、测试、部署到课程演示的全过程。")
    heading(doc, "1.3 软件配置管理计划的维护", 2)
    para(doc, "本计划由项目经理和配置管理工程师共同制订。若 SCM 活动与计划偏离，由配置管理工程师记录问题、发起审计并推动纠正；涉及基线后的业务规则变更时提交 CCB 审批。")
    heading(doc, "1.4 术语", 2)
    table(doc, [
        ["缩略语", "含义"],
        ["SCM", "Software Configuration Management，软件配置管理"],
        ["CI", "Configuration Item，配置项"],
        ["CCB", "Configuration Control Board，配置变更控制委员会"],
        ["FCA", "功能配置审计，验证功能满足需求"],
        ["PCA", "物理配置审计，验证配置项与基线一致"],
    ])
    heading(doc, "1.5 参考资料", 2)
    for item in [
        "IEEE Std 828-2012 Configuration Management in Systems and Software Engineering",
        "GB/T 11457-2006 信息技术 软件工程术语",
        "项目 README.md、docs/03-configuration-management-plan.md、docs/06-build-guide.md、docs/07-deployment-guide.md",
        "《软件配置管理》课程期末作业模板与评分要求",
    ]:
        bullet(doc, item)

    heading(doc, "2、角色与职责", 1)
    table(doc, [
        ["角色", "职责", "责任人"],
        ["项目经理", "计划项目进度，组织评审和里程碑验收", "曾志洪"],
        ["配置管理工程师", "维护配置项登记表、基线清单、tag、CI/CD 与审计证据", "曾志洪"],
        ["开发工程师", "在受控分支完成后端、管理端和 H5 功能开发", "郑蔚然、刘意沁"],
        ["测试工程师", "执行单元测试、集成测试、构建验证和缺陷回归", "刘意沁"],
        ["资料工程师", "维护构建指南、部署指南、课程报告和变更单", "陈灏言"],
        ["SCCB/CCB", "审批基线后变更和重大风险处理", "项目经理、配置管理工程师、测试负责人"],
    ])

    heading(doc, "3．配置项识别", 1)
    heading(doc, "3.1 文档", 2)
    table(doc, [
        ["文档名称", "版本", "负责人", "标识", "存放路径"],
        ["配置管理计划", "V1.0", "配置管理工程师", "CI-DOC-007", "docs/03-configuration-management-plan.md"],
        ["配置项登记表", "V1.0", "配置管理工程师", "CI-DOC-014", "docs/配置项登记表.md"],
        ["基线清单", "V1.0", "配置管理工程师", "CI-DOC-015", "docs/基线清单.md"],
        ["构建指导书", "V1.0", "资料工程师", "CI-DOC-010", "docs/06-build-guide.md"],
        ["部署指南", "V1.0", "资料工程师", "CI-DOC-011", "docs/07-deployment-guide.md"],
    ])
    heading(doc, "3.2 代码与构建脚本", 2)
    para(doc, "源代码、测试代码、pom.xml、package.json、Dockerfile、docker-compose.yml、Jenkinsfile、GitHub Actions workflow、nginx 配置和数据库脚本均纳入配置管理。配置项使用文件路径 + Git commit SHA + tag 进行唯一标识。")
    heading(doc, "3.3 配置项登记证据", 2)
    para(doc, "当前配置项登记表共识别 76 个配置项，覆盖源代码、构建脚本、CI/CD、运行配置、数据库脚本、文档和基线产物。")
    image(doc, "06-config-docs.png", "图1 配置管理文档、配置项登记表与基线清单截图")

    heading(doc, "4、配置库管理", 1)
    heading(doc, "4.1 配置管理工具", 2)
    para(doc, "项目采用 Git + GitHub 作为配置库管理工具，本地仓库位于 F:\\trae code\\petcare-o2o，远程仓库为 https://github.com/zengmo20224/-------2-.git。")
    heading(doc, "4.2 配置库目录结构", 2)
    table(doc, [
        ["目录", "配置项类型", "说明"],
        ["src/main/java", "后端源码", "Spring Boot 业务代码"],
        ["frontend/admin-web", "管理端源码", "Vue 3 管理后台"],
        ["frontend/miniapp", "H5 源码", "UniApp 用户端"],
        ["docs", "配置管理文档", "CMP、登记表、基线、变更、构建部署指南"],
        ["Jenkinsfile / .github/workflows", "CI/CD", "持续集成与部署流水线"],
        ["Dockerfile / docker-compose.yml / nginx", "部署配置", "容器化构建与运行配置"],
    ])
    heading(doc, "4.3 配置库权限管理", 2)
    table(doc, [
        ["目录", "项目经理", "开发工程师", "测试工程师", "资料工程师", "配置管理工程师", "CCB"],
        ["docs", "R", "R", "R", "RW", "RW", "R"],
        ["src/main/java", "R", "RW", "R", "R", "RW", "R"],
        ["frontend", "R", "RW", "R", "R", "RW", "R"],
        ["Jenkinsfile/.github", "R", "R", "R", "R", "RW", "R"],
        ["main/develop 分支", "R", "PR", "R", "R", "RW", "审批"],
    ])

    heading(doc, "5、基线管理", 1)
    heading(doc, "5.1 基线计划", 2)
    table(doc, [
        ["基线名称", "基线时间", "配置项"],
        ["功能基线 v1.0.0-fb", "2026.06.13", "需求与范围文档"],
        ["M1-M6 里程碑基线", "2026.06.13-2026.06.14", "源码、测试、文档、数据库脚本"],
        ["产品基线 v1.0.0-rc1", "2026.06.23", "M1-M6 全部交付 + CI/CD + Docker 化部署"],
    ])
    heading(doc, "5.2 基线活动", 2)
    for item in [
        "基线建立前运行构建、测试和配置项一致性检查。",
        "在 Git 上打 tag，例如 v1.0.0-m1、v1.0.0-m6、v1.0.0-rc1。",
        "更新 docs/基线清单.md，记录 tag、日期、commit SHA、变更摘要和审计状态。",
        "基线后变更必须提交变更申请单并经过 CCB 审批。",
    ]:
        numbered(doc, item)
    image(doc, "02-git-baseline-tags.png", "图2 Git 基线 tag 与分支模型截图")

    heading(doc, "6、变更管理", 1)
    heading(doc, "6.1 变更管理流程", 2)
    para(doc, "变更流程为：提交申请 → 影响分析 → CCB 审批 → 受控分支实施 → CI/回归验证 → 更新配置项与基线 → 归档关闭。")
    table(doc, [
        ["变更类别", "说明", "审批方式"],
        ["A 类", "文档错别字、注释、非行为性调整", "配置管理员审批"],
        ["B 类", "新增配置管理资产、CI/CD、部署脚本", "CCB 评审"],
        ["C 类", "认证、订单金额、库存、预约容量、数据库结构等核心规则", "CCB 评审 + 用户确认"],
    ])
    heading(doc, "6.2 变更实例", 2)
    para(doc, "项目已归档两个真实变更申请单：CR-20260613-001 登录方式变更、CR-20260622-002 配置管理基线建立。")
    image(doc, "07-change-control.png", "图3 变更申请单审批、实施和配置审计记录截图")

    heading(doc, "7、配置审计", 1)
    para(doc, "配置审计分为 FCA、PCA 和过程审计。FCA 验证 M1-M6 功能满足需求，PCA 验证配置项登记表与 Git tag、目录结构一致，过程审计检查提交规范、分支模型、变更单和 CI 结果。")
    image(doc, "09-coverage-evidence.png", "图4 Jenkins、JaCoCo 与测试通过历史证据截图")

    heading(doc, "8、构建与发布管理", 1)
    para(doc, "Jenkinsfile 定义自动编译、测试、打包、Docker 构建、部署和健康检查阶段；GitHub Actions 作为补充 CI，push/PR 时执行后端和前端构建测试。")
    image(doc, "08-ci-pipeline-files.png", "图5 Jenkins 与 GitHub Actions 流水线配置截图")
    image(doc, "05-docker-compose-health.png", "图6 当前 Docker Compose 部署健康状态截图")

    heading(doc, "9、配置状态报告", 1)
    table(doc, [
        ["报告类型", "报告发布周期", "面向人员"],
        ["基线报告", "每个里程碑或产品基线建立后", "全体项目成员、CCB"],
        ["变更报告", "有基线后变更时", "设计、开发、测试、配置管理人员"],
        ["缺陷管理报告", "每两周或重大缺陷关闭后", "开发、测试、项目经理"],
        ["审计报告", "FCA/PCA 或课程验收前", "全体项目成员、教师"],
    ])

    heading(doc, "10、问题跟踪与改进", 1)
    table(doc, [
        ["发现的问题", "问题分类", "责任主体", "计划完成日期", "当前状态"],
        ["前端 npm audit 存在 high/moderate 漏洞", "依赖安全", "前端组", "课程演示后第一轮维护", "待评估修复影响"],
        ["管理端构建存在大 chunk 警告", "性能优化", "前端组", "后续迭代", "不阻塞交付"],
        ["Jenkins 凭据未配置时部署阶段需跳过", "环境配置", "配置管理员", "已处理", "已在 Jenkinsfile 优雅跳过"],
    ])

    doc.save(str(OUT_CM))


def report():
    doc = Document(str(REPORT_TEMPLATE))
    clear_document(doc)
    normalize_doc(doc)
    cover(doc, "", report=True)

    heading(doc, "目录", 1)
    for item in [
        "1 课程设计目的",
        "2 项目基本信息",
        "3 git开发功能完成及日志情况（10分）",
        "4 配置管理计划落实情况（20分）",
        "5 持续集成完成情况（20分）",
        "6 总结",
        "参考文献",
    ]:
        para(doc, item)
    doc.add_page_break()

    heading(doc, "1 课程设计目的", 1)
    for item in [
        "掌握配置库的管理和维护，能够在版本库上创建目录结构并提交配置管理文档。",
        "掌握 Git 分支、合并、提交日志、tag 基线和版本发布管理。",
        "掌握基线后的变更申请、审批、实施、验证和归档流程。",
        "掌握 Jenkins / GitHub Actions 持续集成环境配置，实现自动编译、测试、打包、部署和报告归档。",
        "能够结合实际项目编制软件配置管理计划并用截图证据证明计划落实。",
    ]:
        bullet(doc, item)

    heading(doc, "2 项目基本信息", 1)
    para(doc, "1）小组项目名称：PetCare O2O 宠物门店 O2O 服务平台")
    para(doc, "2）GitHub 路径：https://github.com/zengmo20224/-------2-.git")
    para(doc, "3）项目说明：本项目为《软件配置管理》课程期末大作业，采用 Spring Boot + Vue 3 + UniApp + MySQL + Docker Compose + Jenkins 技术栈。")
    image(doc, "06-config-docs.png", "图1 项目配置管理文档、配置项数量和基线清单截图")

    heading(doc, "3 git开发功能完成及日志情况（10分）", 1)
    para(doc, "项目采用 Git 管理源代码和文档。main 为稳定基线分支，develop 为集成分支，phase-* 分支记录里程碑阶段，fix/* 和 codex/* 分支用于修复或回滚保护。")
    table(doc, [
        ["成员", "完成内容"],
        ["曾志洪", "建立配置管理计划、基线 tag、Jenkinsfile、Docker Compose 部署和课程文档证据"],
        ["郑蔚然", "用户端 H5 页面、管理端页面、前端构建与联调"],
        ["刘意沁", "后端接口测试、配置项核对和文档审查"],
        ["陈灏言", "数据库脚本、部署验证和变更单整理"],
    ])
    image(doc, "02-git-baseline-tags.png", "图2 Git tag、分支和远程仓库截图")
    image(doc, "03-git-log-status.png", "图3 最近提交日志与工作区状态截图")

    heading(doc, "4 配置管理计划落实情况（20分）", 1)
    para(doc, "配置管理计划已从配置项识别、版本库管理、分支管理、基线管理、变更管理、配置审计和发布管理七个方面落地。")
    heading(doc, "4.1 版本库管理工作及证据", 2)
    para(doc, "项目目录结构与配置管理计划一致，docs 存放配置管理文档，src 和 frontend 存放代码，Jenkinsfile、Dockerfile、docker-compose.yml 和 .github/workflows 纳入受控。")
    image(doc, "06-config-docs.png", "图4 配置库目录结构、配置项登记和基线证据")

    heading(doc, "4.2 文档管理工作及证据", 2)
    para(doc, "重要文档包括配置管理计划、配置项登记表、基线清单、变更申请单、构建指导书、部署指南和 README，均存放在配置库中。")
    table(doc, [
        ["文档", "配置项标识", "状态"],
        ["配置管理计划", "CI-DOC-007", "基线"],
        ["构建指导书", "CI-DOC-010", "受控"],
        ["部署指南", "CI-DOC-011", "受控"],
        ["配置项登记表", "CI-DOC-014", "受控"],
        ["基线清单", "CI-DOC-015", "受控"],
    ])

    heading(doc, "4.3 分支管理工作及证据", 2)
    para(doc, "项目采用简化 GitFlow：main 作为稳定主干，develop 作为集成分支，phase-* 作为里程碑分支，hotfix/fix 分支用于紧急修复。")
    image(doc, "02-git-baseline-tags.png", "图5 分支模型与基线 tag 证据")

    heading(doc, "4.4 基线管理工作及证据", 2)
    para(doc, "已建立 8 个基线 tag：v1.0.0-fb、v1.0.0-m1、v1.0.0-m2、v1.0.0-m3、v1.0.0-m4、v1.0.0-m5、v1.0.0-m6、v1.0.0-rc1。")
    image(doc, "02-git-baseline-tags.png", "图6 基线 tag 清单截图")

    heading(doc, "4.5 变更管理工作及证据", 2)
    para(doc, "基线后的登录方式变更和配置管理基线建立均已通过变更申请单记录，包含影响分析、CCB 审批、实施记录、测试结果和配置审计结论。")
    image(doc, "07-change-control.png", "图7 变更申请单实例截图")

    heading(doc, "4.6 版本管理工作及证据", 2)
    para(doc, "版本号采用 SemVer + 里程碑后缀，产品基线为 v1.0.0-rc1。构建产物和 Docker 镜像通过 commit/tag 追溯。")
    image(doc, "04-build-artifacts.png", "图8 后端、管理端和 H5 构建产物截图")

    heading(doc, "5 持续集成完成情况（20分）", 1)
    para(doc, "本项目已完成以下自动化工作：自动编译、自动打包、自动测试、Docker 镜像构建、Docker Compose 部署、健康检查、JUnit/JaCoCo 报告归档、邮件通知模板。")
    for item in ["自动编译：Jenkins Backend Build 阶段执行 mvn clean compile。", "自动测试：Backend Test 阶段执行 mvn test 并归档 JUnit 与 JaCoCo。", "自动打包：Backend Package 阶段归档 petcare-o2o-api jar。", "自动部署：Deploy 阶段执行 docker compose up -d --wait。", "自动报告：JaCoCo 覆盖率与 Jenkins 构建历史作为审计证据归档。"]:
        bullet(doc, item)
    image(doc, "08-ci-pipeline-files.png", "图9 Jenkinsfile 与 GitHub Actions 配置截图")
    image(doc, "09-coverage-evidence.png", "图10 Jenkins 构建历史、843 个测试与 JaCoCo 覆盖率证据")
    image(doc, "05-docker-compose-health.png", "图11 当前 Docker Compose 四服务 healthy 和 HTTP 可访问截图")
    image(doc, "10-admin-web-page.png", "图12 管理端 Web 可访问截图")
    image(doc, "11-h5-page.png", "图13 用户端 H5 可访问截图", width_cm=7.0)
    image(doc, "12-api-health-page.png", "图14 API 健康端点可访问截图", width_cm=13.0)

    heading(doc, "5.1 亮点、创新点", 2)
    for item in [
        "配置项登记表细化到 76 个 CI，覆盖源码、构建、CI/CD、运行配置、数据库、文档和基线产物。",
        "Docker Compose 将 MySQL、API、管理端和 H5 统一编排，课程演示时可一键启动。",
        "Jenkinsfile 支持构建、测试、打包、Docker 构建、部署、健康检查和报告归档，符合持续集成闭环。",
        "基线 tag 与变更申请单可以直接从 Git 和 docs 目录追溯。",
    ]:
        numbered(doc, item)
    heading(doc, "5.2 不足", 2)
    for item in [
        "前端 npm audit 仍提示 high/moderate 漏洞，需要在后续维护中评估依赖升级影响。",
        "管理端构建存在大 chunk 警告，后续可通过路由级代码分割优化。",
        "Jenkins 凭据、邮件服务器等环境项依赖本机配置，正式生产部署应改为密钥管理系统统一注入。",
    ]:
        numbered(doc, item)

    heading(doc, "6 总结", 1)
    for idx, (_, name, _, work) in enumerate(MEMBERS, start=1):
        heading(doc, f"6.{idx} {name}总结", 2)
        para(doc, f"{name}在本次软件配置管理课程设计中主要参与：{work}。通过本次项目实践，掌握了 Git 基线、配置项登记、变更控制、构建部署和持续集成证据整理方法。")

    heading(doc, "参考文献", 1)
    for ref in [
        "[1] IEEE Computer Society. IEEE Std 828-2012 Configuration Management in Systems and Software Engineering[S]. 2012.",
        "[2] GB/T 11457-2006 信息技术 软件工程术语[S].",
        "[3] Docker Inc. Docker Compose Documentation[EB/OL]. https://docs.docker.com/compose/.",
        "[4] Jenkins. Pipeline Syntax Documentation[EB/OL]. https://www.jenkins.io/doc/book/pipeline/.",
        "[5] PetCare O2O 项目 README.md、docs/03-configuration-management-plan.md、docs/06-build-guide.md、docs/07-deployment-guide.md.",
    ]:
        para(doc, ref)

    doc.save(str(OUT_REPORT))


if __name__ == "__main__":
    build_guide()
    cm_plan()
    report()
    print(OUT_BUILD)
    print(OUT_CM)
    print(OUT_REPORT)
