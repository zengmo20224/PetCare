// PetCare O2O 构建和部署指南 — 极简实验报告版
// 对标参考模板《图书管理系统的构建和部署》：
//   实验目的 → 实验环境要求 → 实验内容 → 实验步骤（每步：一句说明+命令+截图+图注）
// 正文统一宋体小四（SimSun 12pt, size:24）

const {
  Document, Packer, Paragraph, TextRun, Footer,
  AlignmentType, HeadingLevel, PageNumber, PageBreak,
  Table, TableRow, TableCell, WidthType, BorderStyle,
  ShadingType, VerticalAlign, ImageRun,
} = require("docx");
const fs = require("fs");
const { imageSize } = require("image-size");

// ============================================================
// 样式常量
// ============================================================
const FONT_CN = "SimSun";            // 宋体
const FONT_EN = "Times New Roman";
const COLOR = "000000";              // 纯黑

const BODY_SIZE = 24;                // 正文：小四 12pt
const H1_SIZE = 32;                  // 一级标题：三号 16pt（居中）
const H2_SIZE = 28;                  // 步骤小标题：四号 14pt
const DOC_TITLE_SIZE = 44;           // 文档主标题：二号 22pt

const BODY_LINE = 360;               // 1.5 倍行距
const BODY_INDENT = 480;             // 首行缩进 2 字符（宋体 12pt）

const SHOTS_DIR = __dirname + "/shots";
const CONTENT_WIDTH_INCH = 6.1;      // 文本区宽度（英寸）

// 边框
const SINGLE = { style: BorderStyle.SINGLE, size: 4, color: "000000" };
const ALL_BORDERS = { top: SINGLE, bottom: SINGLE, left: SINGLE, right: SINGLE };

// ============================================================
// 段落构造器
// ============================================================

// 文档主标题（居中大字）
function docTitle(text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 0, after: 360, line: Math.ceil(22 * 23) },
    children: [new TextRun({
      text, bold: true, size: DOC_TITLE_SIZE, color: COLOR,
      font: { ascii: FONT_EN, eastAsia: FONT_CN },
    })],
  });
}

// 一级标题（“实验目的”等，居中加粗三号）
function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    alignment: AlignmentType.CENTER,
    spacing: { before: 360, after: 200, line: 400 },
    children: [new TextRun({
      text, bold: true, size: H1_SIZE, color: COLOR,
      font: { ascii: FONT_EN, eastAsia: FONT_CN },
    })],
  });
}

// 步骤小标题（“1. 连接到数据库”等，左对齐加粗四号）
function stepTitle(num, text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 280, after: 140, line: 360 },
    children: [new TextRun({
      text: `${num}. ${text}`, bold: true, size: H2_SIZE, color: COLOR,
      font: { ascii: FONT_EN, eastAsia: FONT_CN },
    })],
  });
}

// 正文段落（宋体小四，首行缩进）
function body(text, opts = {}) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    indent: { firstLine: opts.indent === false ? 0 : BODY_INDENT },
    spacing: { line: BODY_LINE, after: opts.after ?? 60 },
    children: [new TextRun({
      text, size: BODY_SIZE, color: COLOR,
      font: { ascii: FONT_EN, eastAsia: FONT_CN },
    })],
  });
}

// 列表项（手动编号，宋体小四，首行缩进）
function listItem(num, text) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    indent: { firstLine: BODY_INDENT },
    spacing: { line: BODY_LINE, after: 40 },
    children: [new TextRun({
      text: `${num}. ${text}`, size: BODY_SIZE, color: COLOR,
      font: { ascii: FONT_EN, eastAsia: FONT_CN },
    })],
  });
}

// 代码块（等宽字体，浅灰底，不缩进）
function code(lines) {
  const arr = Array.isArray(lines) ? lines : [lines];
  const children = [];
  arr.forEach((ln, i) => {
    if (i > 0) children.push(new TextRun({ break: 1 }));
    children.push(new TextRun({
      text: ln, size: 20, color: COLOR,
      font: { ascii: "Courier New", eastAsia: FONT_CN },
    }));
  });
  return new Paragraph({
    alignment: AlignmentType.LEFT,
    indent: { left: 480 },
    spacing: { line: 300, before: 60, after: 120 },
    shading: { type: ShadingType.CLEAR, color: "auto", fill: "F2F2F2" },
    children,
  });
}

// 图片（按文本区宽度等比缩放，居中）
function image(fileName, opts = {}) {
  const fp = SHOTS_DIR + "/" + fileName;
  const buf = fs.readFileSync(fp);
  const dim = imageSize(buf);
  const maxW = opts.maxWidthInch || CONTENT_WIDTH_INCH;
  const ratio = dim.height / dim.width;
  const widthInch = Math.min(dim.width / 96, maxW);
  const heightInch = widthInch * ratio;
  const targetW = Math.round(widthInch * 96);
  const targetH = Math.round(heightInch * 96);
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 100, after: 60, line: 240 },
    children: [new ImageRun({
      data: buf,
      transformation: { width: targetW, height: targetH },
      type: opts.type || "png",
    })],
  });
}

// 图注（图片下方居中，五号灰字）
function caption(text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 0, after: 200, line: 280 },
    children: [new TextRun({
      text, size: 21, color: "595959",
      font: { ascii: FONT_EN, eastAsia: FONT_CN },
    })],
  });
}

// 空段落（留白）
function blank(size = BODY_SIZE) {
  return new Paragraph({
    spacing: { line: 312 },
    children: [new TextRun({ text: "", size })],
  });
}

// 单元格（成员表用）
function cell(text, opts = {}) {
  return new TableCell({
    width: { size: opts.width || 25, type: WidthType.PERCENTAGE },
    margins: { top: 80, bottom: 80, left: 100, right: 100 },
    verticalAlign: VerticalAlign.CENTER,
    borders: ALL_BORDERS,
    children: [new Paragraph({
      alignment: opts.align || AlignmentType.CENTER,
      spacing: { line: 320 },
      children: [new TextRun({
        text: text || "", bold: opts.bold ?? false,
        size: BODY_SIZE, color: COLOR,
        font: { ascii: FONT_EN, eastAsia: FONT_CN },
      })],
    })],
  });
}

// 成员信息表
function memberTable(members) {
  const header = new TableRow({
    tableHeader: true, cantSplit: true,
    children: [
      cell("\u5B66\u53F7", { bold: true, width: 25 }),
      cell("\u59D3\u540D", { bold: true, width: 25 }),
      cell("\u6587\u6863\u8D21\u732E\u5EA6\uFF08\u5747\u503C\u4E3A1\uFF09", { bold: true, width: 28 }),
      cell("\u4F5C\u4E1A\u6210\u7EE9", { bold: true, width: 22 }),
    ],
  });
  const rows = members.map((m) => new TableRow({
    cantSplit: true,
    children: [
      cell(m.id, { width: 25 }),
      cell(m.name, { bold: true, width: 25 }),
      cell(m.contribution, { width: 28 }),
      cell(m.score, { width: 22 }),
    ],
  }));
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    rows: [header, ...rows],
  });
}

// ============================================================
// 文档内容装配
// ============================================================

const members = [
  { id: "", name: "\u3010\u59D3\u540D\u3011", contribution: "1.0", score: "" },
  { id: "", name: "\u3010\u59D3\u540D\u3011", contribution: "1.0", score: "" },
  { id: "", name: "\u3010\u59D3\u540D\u3011", contribution: "1.0", score: "" },
  { id: "", name: "\u3010\u59D3\u540D\u3011", contribution: "1.0", score: "" },
];

const children = [];

// ---------- 标题页 ----------
children.push(blank(28));
children.push(blank(28));
children.push(docTitle("PetCare O2O \u9879\u76EE\u6784\u5EFA\u548C\u90E8\u7F72\u6307\u5357"));
children.push(blank(20));
children.push(memberTable(members));

// 分页（附加到首个章节标题，避免空段落）
// ============================================================
// 实验目的
// ============================================================
children.push(new Paragraph({
  heading: HeadingLevel.HEADING_1,
  alignment: AlignmentType.CENTER,
  pageBreakBefore: true,
  spacing: { before: 0, after: 200, line: 400 },
  children: [new TextRun({
    text: "\u5B9E\u9A8C\u76EE\u7684", bold: true, size: H1_SIZE, color: COLOR,
    font: { ascii: FONT_EN, eastAsia: FONT_CN },
  })],
}));

children.push(body(
  "\u672C\u5B9E\u9A8C\u4EE5 PetCare O2O\uFF08\u9762\u5411\u5355\u4F53\u5BA0\u7269\u95E8\u5E97\u7684 O2O \u670D\u52A1/\u5546\u54C1/\u793E\u533A\u5E73\u53F0\uFF09\u9879\u76EE\u4E3A\u5BF9\u8C61\uFF0C\u5B8C\u6210\u4ECE\u6E90\u7801\u5230\u4E09\u7AEF\u53EF\u8BBF\u95EE\u7684\u5168\u6D41\u7A0B\u3002"
));
children.push(body("\u5B9E\u9A8C\u76EE\u7684\uFF1A"));
children.push(listItem(1, "\u638C\u63E1\u4F7F\u7528 Maven \u5BF9 Spring Boot \u540E\u7AEF\u8FDB\u884C\u7F16\u8BD1\u4E0E\u6253\u5305\u3002"));
children.push(listItem(2, "\u638C\u63E1\u4F7F\u7528 npm \u5BF9\u524D\u7AEF\u8FDB\u884C\u751F\u4EA7\u6784\u5EFA\u3002"));
children.push(listItem(3, "\u638C\u63E1\u4F7F\u7528 Docker Compose \u5B8C\u6210\u4E00\u952E\u90E8\u7F72\u3002"));
children.push(listItem(4, "\u9A8C\u8BC1\u90E8\u7F72\u7ED3\u679C\u5E76\u5B8C\u6210\u622A\u56FE\u4EA4\u4ED8\u3002"));

// ============================================================
// 实验环境要求
// ============================================================
children.push(h1("\u5B9E\u9A8C\u73AF\u5883\u8981\u6C42"));
children.push(listItem("\uFF11", "JDK 17 \u73AF\u5883\u3002"));
children.push(listItem("\uFF12", "Git \u5DF2\u5B89\u88C5\uFF0C\u672C\u5730\u5DF2\u914D\u7F6E Git \u8D26\u53F7\u3002"));
children.push(listItem("\uFF13", "Maven 3.9+ \u73AF\u5883\u3002"));
children.push(listItem("\uFF14", "Node.js 20 LTS \u73AF\u5883\u3002"));
children.push(listItem("\uFF15", "Docker \u53CA Docker Compose \u73AF\u5883\u3002"));
children.push(listItem("\uFF16", "\u5DF2\u5B89\u88C5\u5E76\u542F\u52A8 MySQL 8.0 \u6570\u636E\u5E93\uFF08\u6216\u7531 Docker \u81EA\u52A8\u62C9\u8D77\uFF09\u3002"));
children.push(listItem("\uFF17", "\u5B89\u88C5\u6570\u636E\u5E93\u5BA2\u6237\u7AEF\u5DE5\u5177\uFF0C\u5982 Navicat\u3002"));

// ============================================================
// 实验内容
// ============================================================
children.push(h1("\u5B9E\u9A8C\u5185\u5BB9"));
children.push(listItem(1, "\u514B\u9686\u9879\u76EE\uFF0C\u521D\u59CB\u5316\u6570\u636E\u5E93\u3002"));
children.push(listItem(2, "\u6309\u672C\u673A\u5B9E\u9645\u60C5\u51B5\u4FEE\u6539\u6570\u636E\u5E93\u4E0E\u73AF\u5883\u53D8\u91CF\u914D\u7F6E\u3002"));
children.push(listItem(3, "\u4F7F\u7528 Maven \u6784\u5EFA\u5E76\u6253\u5305\u540E\u7AEF\u3002"));
children.push(listItem(4, "\u4F7F\u7528 Docker Compose \u4E00\u952E\u90E8\u7F72\u5168\u90E8\u670D\u52A1\u3002"));
children.push(listItem(5, "\u8BBF\u95EE\u90E8\u7F72\u597D\u7684\u7BA1\u7406\u7AEF\u4E0E\u7528\u6237\u7AEF\u5E76\u622A\u56FE\u3002"));

// ============================================================
// 实验步骤
// ============================================================
children.push(h1("\u5B9E\u9A8C\u6B65\u9AA4"));

// ---- 步骤 1：克隆项目 ----
children.push(stepTitle(1, "\u514B\u9686\u9879\u76EE\u4EE3\u7801\u5230\u672C\u5730"));
children.push(body(
  "\u5728\u5B58\u50A8\u9879\u76EE\u7684\u6587\u4EF6\u5939\u4E0B\u5355\u51FB\u9F20\u6807\u53F3\u952E\uFF0C\u9009\u3010Git Bash\u3011\uFF0C\u8F93\u5165\u4EE5\u4E0B\u547D\u4EE4\uFF08\u3010\u59D3\u540D\u3011\u66FF\u6362\u4E3A\u672C\u4EBA\u59D3\u540D\uFF09\uFF1A"
));
children.push(code([
  "git clone https://github.com/zengmo20224/petcare-o2o.git petcare_o2o_\u3010\u59D3\u540D\u3011",
  "cd petcare_o2o_\u3010\u59D3\u540D\u3011",
]));
children.push(body(
  "\u547D\u4EE4\u6267\u884C\u540E\uFF0C\u6587\u4EF6\u5939\u4E2D\u4F1A\u51FA\u73B0\u514B\u9686\u4E0B\u6765\u7684\u9879\u76EE\u4ED3\u5E93\u3002"
));

// ---- 步骤 2：连接数据库 ----
children.push(stepTitle(2, "\u8FDE\u63A5\u5E76\u521D\u59CB\u5316\u6570\u636E\u5E93"));
children.push(body(
  "\u6253\u5F00 Navicat\uFF0C\u4F9D\u6B21\u5355\u51FB\u3010\u6587\u4EF6\u3011\u279C\u3010\u65B0\u5EFA\u8FDE\u63A5...\u3011\uFF0C\u8F93\u5165\u672C\u673A\u6570\u636E\u5E93\u7684 IP\u3001\u7AEF\u53E3\u3001\u8D26\u53F7\u540E\u70B9\u3010\u786E\u5B9A\u3011\u3002\u672C\u4F8B\u6570\u636E\u5E93\u5728\u672C\u673A\uFF0C\u9ED8\u8BA4\u7AEF\u53E3 3306\uFF08Docker \u90E8\u7F72\u65F6\u4E3A 3307\uFF09\uFF0C\u540C\u5B66\u4EEC\u9700\u6839\u636E\u81EA\u5DF1\u7684\u5B9E\u9645\u60C5\u51B5\u8FDB\u884C\u8BBE\u7F6E\u3002"
));
children.push(body(
  "\u5728\u6570\u636E\u5E93\u8FDE\u63A5\u8282\u70B9\u4E0A\u53F3\u952E\uFF0C\u9009\u3010\u8FD0\u884C SQL \u6587\u4EF6...\u3011\uFF0C\u9009\u62E9\u9879\u76EE\u6839\u76EE\u5F55\u4E0B\u7684 schema.sql \u540E\u70B9\u3010\u5F00\u59CB\u3011\uFF0C\u7CFB\u7EDF\u4F1A\u521B\u5EFA petcare_o2o \u6570\u636E\u5E93\u53CA\u5168\u90E8\u4E1A\u52A1\u8868\u3002\u9700\u8981\u6F14\u793A\u6570\u636E\u65F6\uFF0C\u518D\u8FD0\u884C src/main/resources/data-dev.sql \u704C\u5165\u79CD\u5B50\u6570\u636E\u3002"
));

// ---- 步骤 3：修改配置 ----
children.push(stepTitle(3, "\u4FEE\u6539\u7A0B\u5E8F\u7684\u6570\u636E\u5E93\u4E0E\u73AF\u5883\u914D\u7F6E"));
children.push(body(
  "\u6839\u636E\u672C\u673A\u5B9E\u9645\u7AEF\u53E3\u4E0E\u8D26\u53F7\u4FEE\u6539\u914D\u7F6E\u3002\u672C\u5730\u5F00\u53D1\u7F16\u8F91 src/main/resources/application-dev.yml\uFF0C\u4FEE\u6539\u6570\u636E\u6E90 url \u4E2D\u7684 host\u3001port\u3001\u8D26\u53F7\u5BC6\u7801\u3002"
));
children.push(body("\u540C\u65F6\u590D\u5236\u9879\u76EE\u6839\u76EE\u5F55\u7684 .env.example \u4E3A .env \u5E76\u586B\u5199\uFF1A"));
children.push(code([
  "# Windows PowerShell",
  "Copy-Item .env.example .env",
  "",
  "# \u91CD\u70B9\u5B57\u6BB5\uFF1ADB_PASSWORD\u3001JWT_SECRET\uFF08\u226532 \u5B57\u7B26\uFF09",
]));

// ---- 步骤 4：Maven 打包 ----
children.push(stepTitle(4, "\u4F7F\u7528 Maven \u6784\u5EFA\u5E76\u6253\u5305\u540E\u7AEF"));
children.push(body(
  "\u8FDB\u5165\u9879\u76EE\u6839\u76EE\u5F55\uFF0C\u6253\u5F00\u547D\u4EE4\u884C\u7A97\u53E3\uFF0C\u6267\u884C\u4EE5\u4E0B\u547D\u4EE4\uFF08\u6253\u5305\u6210\u529F\u540E\u4FDD\u5B58\u622A\u56FE\uFF09\uFF1A"
));
children.push(code([
  "mvn clean package -DskipTests",
  "# \u4EA7\u7269\uFF1Atarget/petcare-o2o-api-0.1.0-SNAPSHOT.jar",
]));
children.push(image("04_h5_home.png", { maxWidthInch: 3.2 }));
children.push(caption("\u56FE1\u3001\u7528\u6237\u7AEF H5 \u9996\u9875"));
// 说明：此处置 H5 首页图，是因为它最能直观体现构建产物效果

// ---- 步骤 5：Docker 部署 ----
children.push(stepTitle(5, "\u4F7F\u7528 Docker Compose \u4E00\u952E\u90E8\u7F72"));
children.push(body(
  "\u672C\u9879\u76EE\u90E8\u7F72\u5F62\u6001\u4E3A Docker Compose \u5355\u673A\u90E8\u7F72\u3002\u5728\u9879\u76EE\u6839\u76EE\u5F55\u6267\u884C\u4E00\u6761\u547D\u4EE4\u5373\u53EF\u6784\u5EFA\u955C\u50CF\u3001\u542F\u52A8 MySQL\u3001API\u3001\u7BA1\u7406\u7AEF\u3001H5 \u56DB\u4E2A\u670D\u52A1\u5E76\u7B49\u5F85\u5065\u5EB7\u68C0\u67E5\u901A\u8FC7\uFF1A"
));
children.push(code([
  "docker compose up -d --build --wait",
]));
children.push(image("02_docker_ps.png"));
children.push(caption("\u56FE2\u3001docker compose ps\uFF08\u56DB\u4E2A\u670D\u52A1\u5747\u4E3A healthy\uFF09"));

// ---- 步骤 6：访问与验证 ----
children.push(stepTitle(6, "\u8BBF\u95EE\u90E8\u7F72\u597D\u7684\u7A0B\u5E8F"));
children.push(body(
  "\u90E8\u7F72\u6210\u529F\u540E\uFF0C\u7528\u6D4F\u89C8\u5668\u8BBF\u95EE\u4E09\u7AEF\uFF1A"
));
children.push(listItem("\uFF11", "\u7528\u6237\u7AEF H5\uFF1Ahttp://localhost:8081\u3002"));
children.push(listItem("\uFF12", "\u7BA1\u7406\u7AEF\uFF1Ahttp://localhost:8080\uFF08\u8D26\u53F7 admin / admin123456\uFF09\u3002"));
children.push(listItem("\uFF13", "\u540E\u7AEF\u5065\u5EB7\u68C0\u67E5\uFF1Ahttp://localhost:8082/actuator/health\u3002"));
children.push(body("\u6253\u5F00\u7BA1\u7406\u7AEF\u767B\u5F55\u540E\u7684\u4E3B\u754C\u9762\u5982\u4E0B\u56FE\u6240\u793A\uFF1A"));
children.push(image("03_admin_dashboard.png"));
children.push(caption("\u56FE3\u3001\u7BA1\u7406\u7AEF\u767B\u5F55\u540E\u7684\u8FD0\u8425\u603B\u89C8\u4E3B\u754C\u9762"));

// ---- 步骤 7：截图交付 ----
children.push(stepTitle(7, "\u622A\u56FE"));
children.push(body("\u5B8C\u6210\u5982\u4E0B\u8981\u6C42\u540E\u622A\u56FE\uFF1A"));
children.push(listItem("\uFF081\uFF09", "\u547D\u4EE4\u884C\u6267\u884C hostname\uFF0C\u67E5\u8BE2\u8BA1\u7B97\u673A\u540D\uFF1B"));
children.push(listItem("\uFF082\uFF09", "\u5728\u7528\u6237\u7AEF H5 \u7684\u793E\u533A\u6216\u670D\u52A1\u9875\u9762\u7559\u4E0B\u4E2A\u4EBA\u59D3\u540D\u3002"));
children.push(image("01_hostname.png", { maxWidthInch: 5.0 }));
children.push(caption("\u56FE4\u3001hostname \u67E5\u8BE2\u8BA1\u7B97\u673A\u540D"));

children.push(blank(20));
children.push(new Paragraph({
  alignment: AlignmentType.RIGHT,
  spacing: { before: 240, line: 360 },
  children: [new TextRun({
    text: "\u7F16\u5199\u4EBA\uFF1A\u3010\u59D3\u540D\u3011\u3000\u3000\u65E5\u671F\uFF1A2026\u5E7406\u670825\u65E5",
    size: BODY_SIZE, color: COLOR,
    font: { ascii: FONT_EN, eastAsia: FONT_CN },
  })],
}));

// ============================================================
// 文档装配
// ============================================================
const doc = new Document({
  creator: "PetCare O2O \u914D\u7F6E\u7BA1\u7406\u7EC4",
  title: "PetCare O2O \u9879\u76EE\u6784\u5EFA\u548C\u90E8\u7F72\u6307\u5357",
  styles: {
    default: {
      document: {
        run: { font: { ascii: FONT_EN, eastAsia: FONT_CN }, size: BODY_SIZE, color: COLOR },
        paragraph: { spacing: { line: BODY_LINE } },
      },
    },
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },
        margin: { top: 1400, bottom: 1400, left: 1701, right: 1417 },
      },
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [new TextRun({
            children: [PageNumber.CURRENT], size: 20, color: COLOR,
            font: { ascii: FONT_EN, eastAsia: FONT_CN },
          })],
        })],
      }),
    },
    children,
  }],
});

const outDir = "F:/trae code/petcare-o2o/docs";
const out = outDir + "/PetCare O2O\u9879\u76EE\u6784\u5EFA\u548C\u90E8\u7F72\u6307\u5357.docx";
Packer.toBuffer(doc).then((buf) => {
  try {
    fs.writeFileSync(out, buf);
    console.log("OK ->", out);
  } catch (e) {
    if (e.code === "EBUSY" || e.code === "EPERM") {
      const tmp = outDir + "/PetCare O2O\u9879\u76EE\u6784\u5EFA\u548C\u90E8\u7F72\u6307\u5357_\u65B0.docx";
      fs.writeFileSync(tmp, buf);
      console.log("OK (\u4E3B\u6587\u4EF6\u88AB\u5360\u7528\uFF0C\u5DF2\u5199\u5165\u4E34\u65F6\u6587\u4EF6) ->", tmp);
    } else { throw e; }
  }
});
