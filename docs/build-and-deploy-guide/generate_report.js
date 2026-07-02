// PetCare O2O 软件配置管理课程设计报告 — 模板填写版
// 基于学校《课程设计报告模板》结构，填入 PetCare O2O 真实内容
// 正文：宋体(SimSun) 小四(12pt)
// 作者：曾志洪  学号：2320160033  （单人项目）

const {
  Document, Packer, Paragraph, TextRun, Footer,
  AlignmentType, HeadingLevel, PageNumber, PageBreak,
  Table, TableRow, TableCell, WidthType, BorderStyle,
  ShadingType, VerticalAlign, TableOfContents, StyleLevel,
  SectionType, NumberFormat, ImageRun,
} = require("docx");
const fs = require("fs");
const { imageSize } = require("image-size");

// 样式常量
const CN = "SimSun", EN = "Times New Roman", MONO = "Courier New", COLOR = "000000";
const BODY = 24, H1S = 32, H2S = 28, H3S = 26, TITLE = 44, COVER_LBL = 28;
const LINE = 360, INDENT = 480;
const SGL = { style: BorderStyle.SINGLE, size: 4, color: "000000" };
const BOX = { top: SGL, bottom: SGL, left: SGL, right: SGL };

const SHOTS = __dirname + "/shots";               // 界面图
const ESHOT = __dirname + "/shots/report_shots";  // 证据图
const CONTENT_W = 6.1;

const ME = "\u66FE\u5FD7\u6D2A";   // 曾志洪
const SID = "2320160033";
const REPO = "https://github.com/zengmo20224/petcare-o2o";

// 构造器
function R(t, o = {}) {
  return new TextRun({
    text: String(t == null ? "" : t),
    size: o.size || BODY, color: o.color || COLOR, bold: o.bold || false,
    italics: o.italics || false, underline: o.underline,
    font: { ascii: o.en || EN, eastAsia: o.cn || CN },
  });
}
function P(t, o = {}) {
  return new Paragraph({
    alignment: o.align || AlignmentType.JUSTIFIED,
    indent: { firstLine: o.indent === false ? 0 : INDENT },
    spacing: { line: LINE, after: o.after ?? 60 },
    children: Array.isArray(t) ? t : [R(t, o)],
  });
}
function PN(t, o = {}) { return P(t, { ...o, indent: false }); }
function LI(n, t, o = {}) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED, indent: { firstLine: INDENT },
    spacing: { line: LINE, after: 40 }, children: [R(`${n} ${t}`, o)],
  });
}
function H1(t) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 360, after: 200, line: 400 },
    children: [R(t, { bold: true, size: H1S })],
  });
}
function H2(t) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 280, after: 140, line: 360 },
    children: [R(t, { bold: true, size: H2S })],
  });
}
function CODE(lines) {
  const arr = Array.isArray(lines) ? lines : [lines];
  const ch = [];
  arr.forEach((ln, i) => {
    if (i > 0) ch.push(new TextRun({ break: 1 }));
    ch.push(new TextRun({ text: ln, size: 20, color: COLOR, font: { ascii: MONO, eastAsia: CN } }));
  });
  return new Paragraph({
    alignment: AlignmentType.LEFT, indent: { left: 480 },
    spacing: { line: 300, before: 60, after: 120 },
    shading: { type: ShadingType.CLEAR, color: "auto", fill: "F2F2F2" }, children: ch,
  });
}
function BLANK(s = BODY) {
  return new Paragraph({ spacing: { line: 312 }, children: [new TextRun({ text: "", size: s })] });
}
function CELL(t, o = {}) {
  const lines = String(t == null ? "" : t).split("\n");
  return new TableCell({
    width: { size: o.width || 100, type: WidthType.PERCENTAGE },
    margins: { top: 60, bottom: 60, left: 80, right: 80 },
    verticalAlign: VerticalAlign.CENTER, borders: BOX,
    children: lines.map((ln) => new Paragraph({
      alignment: o.align || AlignmentType.CENTER, spacing: { line: 300 },
      children: [R(ln, { bold: o.bold || false, size: o.size || BODY })],
    })),
  });
}
function TBL(headers, rows, widths, leftCols = []) {
  const w = widths || headers.map(() => Math.floor(100 / headers.length));
  const hr = new TableRow({ tableHeader: true, cantSplit: true,
    children: headers.map((t, i) => CELL(t, { bold: true, width: w[i] })) });
  const dr = rows.map((r) => new TableRow({ cantSplit: true,
    children: r.map((v, i) => CELL(v, { width: w[i], align: leftCols.includes(i) ? AlignmentType.LEFT : AlignmentType.CENTER })) }));
  return new Table({ width: { size: 100, type: WidthType.PERCENTAGE }, rows: [hr, ...dr] });
}
// 图片（等比缩放居中）
function IMG(dir, name, o = {}) {
  const fp = dir + "/" + name;
  const buf = fs.readFileSync(fp);
  const dim = imageSize(buf);
  const maxW = o.maxW || CONTENT_W;
  const ratio = dim.height / dim.width;
  const wIn = Math.min(dim.width / 96, maxW);
  const hIn = wIn * ratio;
  return new Paragraph({
    alignment: AlignmentType.CENTER, spacing: { before: 100, after: 40, line: 240 },
    children: [new ImageRun({ data: buf, transformation: { width: Math.round(wIn * 96), height: Math.round(hIn * 96) }, type: o.type || "png" })],
  });
}
function shot(name, o) { return IMG(SHOTS, name, o); }
function eshot(name, o) { return IMG(ESHOT, name, o); }
function CAP(t) {
  return new Paragraph({
    alignment: AlignmentType.CENTER, spacing: { before: 0, after: 200, line: 280 },
    children: [R(t, { size: 21, color: "595959" })],
  });
}
// 勾选项（复选框样式）
function CHECK(checked, text) {
  const mark = checked ? "\u2611" : "\u2610"; // ☑ / ☐
  return new Paragraph({
    alignment: AlignmentType.LEFT, indent: { left: 480 },
    spacing: { line: LINE, after: 50 },
    children: [R(`${mark}  ${text}`)],
  });
}

const A = [];

// ============================================================
// 第 1 章 课程设计目的
// ============================================================
A.push(H1("1 \u8BFE\u7A0B\u8BBE\u8BA1\u76EE\u7684"));
A.push(P("\u672C\u8BFE\u7A0B\u8BBE\u8BA1\u4EE5 PetCare O2O\uFF08\u9762\u5411\u5355\u4F53\u5BA0\u7269\u95E8\u5E97\u7684 O2O \u670D\u52A1/\u5546\u54C1/\u793E\u533A\u5E73\u53F0\uFF09\u9879\u76EE\u4E3A\u8F7D\u4F53\uFF0C\u901A\u8FC7\u5B8C\u6574\u7684\u8F6F\u4EF6\u914D\u7F6E\u7BA1\u7406\uFF08SCM\uFF09\u5B9E\u8DF5\uFF0C\u8FBE\u5230\u4EE5\u4E0B\u76EE\u7684\uFF1A"));
A.push(LI("1.", "\u638C\u63E1\u914D\u7F6E\u5E93\u7684\u7BA1\u7406\u548C\u7EF4\u62A4\u3002"));
A.push(LI("2.", "\u80FD\u591F\u7BA1\u7406\u548C\u914D\u7F6E\u6301\u7EED\u96C6\u6210\u73AF\u5883\u3002"));
A.push(LI("3.", "\u719F\u7EC3\u4F7F\u7528\u7248\u672C\u914D\u7F6E\u7BA1\u7406\u5DE5\u5177\uFF08Git\uFF09\u548C\u6301\u7EED\u96C6\u6210\u5DE5\u5177\uFF08Jenkins\uFF09\u3002"));
A.push(LI("4.", "\u80FD\u591F\u8FDB\u884C\u6587\u6863\u7BA1\u7406\u3002\u80FD\u591F\u5728\u7248\u672C\u5E93\u4E0A\u521B\u5EFA\u8981\u6C42\u7684\u76EE\u5F55\u7ED3\u6784\uFF0C\u63D0\u4EA4\u76F8\u5173\u6587\u6863\u5230\u6307\u5B9A\u76EE\u5F55\u4E0B\u3002"));
A.push(LI("5.", "\u80FD\u591F\u8FDB\u884C\u5206\u652F\u7BA1\u7406\u3002\u4E86\u89E3\u5206\u652F\u7B56\u7565\uFF0C\u5E76\u80FD\u521B\u5EFA\u5206\u652F\u548C\u5408\u5E76\u5206\u652F\u3002"));
A.push(LI("6.", "\u4E86\u89E3\u57FA\u7EBF\u7BA1\u7406\u3002"));
A.push(LI("7.", "\u638C\u63E1\u53D8\u66F4\u7BA1\u7406\u3002\u57FA\u7EBF\u4FEE\u6539\u65F6\u9700\u8981\u63D0\u4EA4\u53D8\u66F4\u7533\u8BF7\u5355\uFF0C\u5BA1\u6279\u901A\u8FC7\u540E\u65B9\u80FD\u8FDB\u884C\u53D8\u66F4\u3002"));
A.push(LI("8.", "\u80FD\u591F\u6839\u636E\u9879\u76EE\u60C5\u51B5\u7F16\u5236\u914D\u7F6E\u7BA1\u7406\u8BA1\u5212\u3002"));

// ============================================================
// 第 2 章 项目基本信息
// ============================================================
A.push(H1("2 \u9879\u76EE\u57FA\u672C\u4FE1\u606F"));
A.push(PN(`1\uFF09\u5C0F\u7EC4\u9879\u76EE\u540D\u79F0\uFF1APetCare O2O\uFF08\u9762\u5411\u5355\u4F53\u5BA0\u7269\u95E8\u5E97\u7684 O2O \u670D\u52A1/\u5546\u54C1/\u793E\u533A\u5E73\u53F0\uFF09\u3002`));
A.push(PN(`2\uFF09GitHub \u8DEF\u5F84\uFF1A${REPO}`));
A.push(PN("3\uFF09Git \u4EE3\u7801\u8D21\u732E\u6392\u540D\u622A\u56FE\uFF1A"));
A.push(P("\u672C\u9879\u76EE\u4E3A\u5355\u4EBA\u8BFE\u7A0B\u4F5C\u4E1A\uFF0C\u5168\u90E8 179 \u6B21\u63D0\u4EA4\u5747\u7531\u66FE\u5FD7\u6D2A\uFF08\u8D26\u53F7 2727649zeng\uFF09\u72EC\u7ACB\u5B8C\u6210\uFF0C\u8D21\u732E\u6392\u540D\u7B2C\u4E00\u3002\u4E0B\u56FE\u4E3A git shortlog \u8D21\u732E\u8005\u7EDF\u8BA1\u622A\u56FE\u3002"));
A.push(eshot("e03_git_log.png"));
A.push(CAP("\u56FE2-1  Git \u63D0\u4EA4\u65E5\u5FD7\u4E0E\u8D21\u732E\u8005\u7EDF\u8BA1\uFF08179 \u6B21\u63D0\u4EA4\uFF0C\u5168\u90E8\u9075\u5FAA Conventional Commits\uFF09"));

// ============================================================
// 第 3 章 git开发功能完成及日志情况
// ============================================================
A.push(H1("3 git \u5F00\u53D1\u529F\u80FD\u5B8C\u6210\u53CA\u65E5\u5FD7\u60C5\u51B5\uFF0815\u5206\uFF09"));
A.push(P(
  `${ME}\uFF1A\u72EC\u7ACB\u5B8C\u6210\u9879\u76EE\u5168\u90E8\u529F\u80FD\u5F00\u53D1\u3002PetCare O2O \u8986\u76D6\u5BA0\u7269\u670D\u52A1\u9884\u7EA6\u3001\u5546\u54C1\u96F6\u552E\u3001\u793E\u533A\u4E92\u52A8\u3001\u8425\u9500\u6D3B\u52A8\u56DB\u5927\u4E1A\u52A1\u57DF\uFF0C\u5305\u542B\u540E\u7AEF API\u3001\u7BA1\u7406\u7AEF PC Web\u3001\u7528\u6237\u7AEF H5 \u4E09\u7AEF\uFF0C\u4EA7\u54C1\u57FA\u7EBF v1.0.0-rc1 \u5DF2\u5EFA\u7ACB\uFF0C843 \u4E2A\u6D4B\u8BD5\u5168\u90E8\u901A\u8FC7\u3002`
));
A.push(PN("\u4E3B\u8981\u5F00\u53D1\u529F\u80FD\u754C\u9762\u4E0E\u65E5\u5FD7\u622A\u56FE\u5982\u4E0B\uFF1A"));
A.push(PN("\uFF081\uFF09\u7528\u6237\u7AEF H5 \u529F\u80FD\u754C\u9762\uFF1A", { bold: true }));
A.push(shot("04_h5_home.png", { maxW: 3.4 }));
A.push(CAP("\u56FE3-1  \u7528\u6237\u7AEF H5 \u9996\u9875\uFF08\u670D\u52A1\u9884\u7EA6\u3001\u5546\u54C1\u3001\u793E\u533A\u3001\u8425\u9500\u6D3B\u52A8\uFF09"));
A.push(PN("\uFF082\uFF09\u7BA1\u7406\u7AEF\u540E\u53F0\u529F\u80FD\u754C\u9762\uFF1A", { bold: true }));
A.push(shot("03_admin_dashboard.png"));
A.push(CAP("\u56FE3-2  \u7BA1\u7406\u7AEF\u767B\u5F55\u540E\u7684\u8FD0\u8425\u603B\u89C8\u4E3B\u754C\u9762\uFF08\u9884\u7EA6/\u8BA2\u5355/\u5546\u54C1/\u793E\u533A\u7BA1\u7406\uFF09"));
A.push(PN("\uFF083\uFF09Git \u5F00\u53D1\u65E5\u5FD7\u622A\u56FE\uFF1A", { bold: true }));
A.push(eshot("e03_git_log.png"));
A.push(CAP("\u56FE3-3  git log \u63D0\u4EA4\u65E5\u5FD7\uFF08\u9075\u5FAA Conventional Commits\uFF1Afeat/fix/docs/ci/build \u7B49 type\uFF09"));

// ============================================================
// 第 4 章 配置管理计划落实情况
// ============================================================
A.push(H1("4 \u914D\u7F6E\u7BA1\u7406\u8BA1\u5212\u843D\u5B9E\u60C5\u51B5\uFF0815\u5206\uFF09"));
A.push(P(
  "\u672C\u5C0F\u7EC4\uFF08\u5355\u4EBA\uFF09\u5206\u522B\u5728\u7248\u672C\u5E93\u3001\u6301\u7EED\u96C6\u6210\u3001\u6587\u6863\u7BA1\u7406\u3001\u5206\u652F\u7BA1\u7406\u3001\u57FA\u7EBF\u7BA1\u7406\u3001\u53D8\u66F4\u7BA1\u7406\u5B8C\u6210\u4E86\u76F8\u5E94\u5DE5\u4F5C\uFF0C\u5E76\u63D0\u4F9B\u7248\u672C\u5E93\u7684\u6807\u7B7E\u3001\u65E5\u5FD7\u7B49\u76F8\u5173\u622A\u56FE\uFF0C\u8BC1\u660E\u9879\u76EE\u662F\u6309\u7167\u914D\u7F6E\u7BA1\u7406\u8BA1\u5212\u5B8C\u6210\u7684\u3002\u9ED8\u8BA4\u8D21\u732E\u5747\u7B49\u3002"
));

A.push(H2("4.1 \u7248\u672C\u5E93\u7BA1\u7406\u5DE5\u4F5C\u53CA\u8BC1\u636E"));
A.push(P("\u7248\u672C\u5E93\u76EE\u5F55\u7ED3\u6784\u4E0E\u914D\u7F6E\u7BA1\u7406\u8BA1\u5212\u4E00\u81F4\uFF0C\u5DE5\u5177\u4E0E\u914D\u7F6E\u7BA1\u7406\u8BA1\u5212\u4E00\u81F4\u3002\u91C7\u7528 Git + GitHub \u4F5C\u4E3A\u7248\u672C\u63A7\u5236\u5DE5\u5177\uFF0C\u4ED3\u5E93\u76EE\u5F55\u5305\u542B src\u3001frontend\u3001docs\u3001nginx\u3001.github \u7B49\uFF0C\u4E0E CMP \u00A74.2 \u89C4\u5212\u7684\u914D\u7F6E\u5E93\u76EE\u5F55\u7ED3\u6784\u5B8C\u5168\u4E00\u81F4\u3002"));
A.push(eshot("e02_git_branch.png"));
A.push(CAP("\u56FE4-1  \u7248\u672C\u5E93\u5206\u652F\u6A21\u578B\uFF08git branch -a\uFF0Cmain/develop/phase-* \u5206\u652F\u9F50\u5168\uFF09"));

A.push(H2("4.2 \u6587\u6863\u7BA1\u7406\u5DE5\u4F5C\u53CA\u8BC1\u636E"));
A.push(P(
  "\u9879\u76EE\u91CD\u8981\u6587\u6863\u7EB3\u5165\u914D\u7F6E\u7BA1\u7406\uFF0C\u5B58\u50A8\u5728\u914D\u7F6E\u5E93 docs/ \u76EE\u5F55\u4E0B\u3002\u5305\u62EC\u9700\u6C42\u89C4\u683C\u3001\u9879\u76EE\u8FB9\u754C\u3001\u67B6\u6784\u8BBE\u8BA1\u3001\u914D\u7F6E\u7BA1\u7406\u8BA1\u5212\u3001\u4EE3\u7801\u89C4\u8303\u3001\u6D4B\u8BD5\u9A8C\u8BC1\u3001\u6784\u5EFA\u6307\u5BFC\u4E66\u3001\u90E8\u7F72\u6307\u5357\u3001\u914D\u7F6E\u9879\u767B\u8BB0\u8868\u3001\u57FA\u7EBF\u6E05\u5355\u3001\u53D8\u66F4\u5B9E\u4F8B\u7B49\u5171 20 \u4F59\u4EFD\u6587\u6863\uFF0C\u5747\u63D0\u4EA4\u5230 docs/ \u6307\u5B9A\u76EE\u5F55\u3002"
));
A.push(PN("\u4E3B\u8981\u6587\u6863\u6E05\u5355\u5982\u4E0B\uFF1A"));
A.push(TBL(
  ["\u6587\u6863\u540D\u79F0", "\u8DEF\u5F84", "\u7248\u672C"],
  [
    ["\u9700\u6C42\u89C4\u683C\u8BF4\u660E\u4E66", "docs/requirements-source.md", "V1.0"],
    ["\u9879\u76EE\u8FB9\u754C", "docs/00-project-boundary.md", "V1.0"],
    ["\u914D\u7F6E\u7BA1\u7406\u8BA1\u5212", "docs/03-configuration-management-plan.md", "V1.0"],
    ["\u6784\u5EFA\u6307\u5BFC\u4E66", "docs/06-build-guide.md", "V1.0"],
    ["\u90E8\u7F72\u6307\u5357", "docs/07-deployment-guide.md", "V1.0"],
    ["\u914D\u7F6E\u9879\u767B\u8BB0\u8868", "docs/\u914D\u7F6E\u9879\u767B\u8BB0\u8868.md", "V1.0"],
    ["\u57FA\u7EBF\u6E05\u5355", "docs/\u57FA\u7EBF\u6E05\u5355.md", "V1.0"],
    ["\u53D8\u66F4\u7533\u8BF7\u5355\u5B9E\u4F8B", "docs/\u53D8\u66F4\u7533\u8BF7\u5355-\u5B9E\u4F8B/", "V1.0"],
  ],
  [32, 48, 20], [0, 1]
));

A.push(H2("4.3 \u5206\u652F\u7BA1\u7406\u5DE5\u4F5C\u53CA\u8BC1\u636E"));
A.push(P(
  "\u56E2\u961F\u6210\u5458\u80FD\u89C4\u8303\u5730\u521B\u5EFA\u5206\u652F\uFF08\u5206\u652F\u547D\u540D\u89C4\u8303\uFF09\uFF0C\u5206\u652F\u62C9\u53BB\u5408\u5E76\u89C4\u8303\uFF0C\u63D0\u4EA4\u65E5\u5FD7\u89C4\u8303\u3002\u91C7\u7528\u7B80\u5316 GitFlow \u6A21\u578B\uFF1Amain\uFF08\u53D1\u5E03\u57FA\u7EBF\uFF09\u3001develop\uFF08\u96C6\u6210\u5206\u652F\uFF09\u3001phase-N-*\uFF08\u91CC\u7A0B\u7891\u5206\u652F\uFF09\u3001hotfix/CR-*\uFF08\u7D27\u6025\u4FEE\u590D\uFF09\u3002main \u4E0E develop \u8BBE\u4E3A\u53D7\u4FDD\u62A4\u5206\u652F\uFF0C\u5408\u5E76\u987B\u7ECF Pull Request \u8BC4\u5BA1\u3002"
));
A.push(PN("\u5B9E\u9645\u4F7F\u7528\u7684\u5206\u652F\u5982\u4E0B\uFF1A"));
A.push(CODE([
  "main                     \u751F\u4EA7\u53D1\u5E03\u57FA\u7EBF\uFF08\u53D7\u4FDD\u62A4\uFF09",
  "develop                  \u65E5\u5E38\u96C6\u6210\u5206\u652F\uFF08\u53D7\u4FDD\u62A4\uFF09",
  "phase-2-backend-skeleton  M0\u2013M2 \u540E\u7AEF\u9AA8\u67B6\u9636\u6BB5",
  "phase-7-product-orders    M4 \u5546\u54C1\u8BA2\u5355\u9636\u6BB5",
  "phase-10-frontend         \u524D\u7AEF\u9636\u6BB5",
  "phase-11-user-prerequisites M3 \u7528\u6237\u4E0E\u9884\u7EA6\u9636\u6BB5",
  "fix/docker-compose-port-conflict  \u7AEF\u53E3\u51B2\u7A81\u4FEE\u590D",
]));
A.push(eshot("e02_git_branch.png"));
A.push(CAP("\u56FE4-2  \u5206\u652F\u7BA1\u7406\u8BC1\u636E\uFF08git branch -a\uFF0C\u547D\u540D\u7B26\u5408 CMP \u00A74.2 \u89C4\u8303\uFF09"));

A.push(H2("4.4 \u57FA\u7EBF\u7BA1\u7406\u5DE5\u4F5C\u53CA\u8BC1\u636E"));
A.push(P(
  "\u7B26\u5408\u914D\u7F6E\u7BA1\u7406\u8BA1\u5212\u5F62\u6210\u57FA\u7EBF\uFF0C\u6253\u6807\u7B7E\u7684\u7248\u672C\u53F7\u7B26\u5408\u914D\u7F6E\u7BA1\u7406\u8BA1\u5212\u4E2D\u7684\u7248\u672C\u547D\u540D\u89C4\u8303\u3002\u91C7\u7528\u8BED\u4E49\u5316\u7248\u672C\u53F7 MAJOR.MINOR.PATCH + \u91CC\u7A0B\u7891\u540E\u7F00\uFF0C\u5171\u6253\u6807\u7B7E 8 \u4E2A\u57FA\u7EBF tag\uFF1Av1.0.0-fb\uFF08\u529F\u80FD\u57FA\u7EBF\uFF09\u3001v1.0.0-m1 \u81F3 v1.0.0-m6\uFF08M1\u2013M6 \u91CC\u7A0B\u7891\u57FA\u7EBF\uFF09\u3001v1.0.0-rc1\uFF08\u4EA7\u54C1\u57FA\u7EBF\uFF09\u3002"
));
A.push(eshot("e01_git_tag.png"));
A.push(CAP("\u56FE4-3  \u57FA\u7EBF\u7BA1\u7406\u8BC1\u636E\uFF08git tag -l\uFF0C8 \u4E2A\u57FA\u7EBF\u7B26\u5408 SemVer \u89C4\u8303\uFF09"));
A.push(TBL(
  ["Tag", "\u57FA\u7EBF\u7C7B\u578B", "\u5EFA\u7ACB\u65E5\u671F", "\u5185\u5BB9\u6982\u8981"],
  [
    ["v1.0.0-fb", "\u529F\u80FD\u57FA\u7EBF", "2026.06.13", "\u786E\u7ACB V1 \u529F\u80FD\u8FB9\u754C\u4E0E H5-first \u4EA4\u4ED8\u6A21\u578B"],
    ["v1.0.0-m1", "\u91CC\u7A0B\u7891\u57FA\u7EBF", "2026.06.13", "H5 \u57FA\u7840\u4E0E\u53EF\u91CD\u590D\u6F14\u793A\u6570\u636E"],
    ["v1.0.0-m3", "\u91CC\u7A0B\u7891\u57FA\u7EBF", "2026.06.13", "\u7528\u6237\u8D44\u6599\u3001\u771F\u5B9E\u9884\u7EA6\uFF08\u542B\u767B\u5F55\u53D8\u66F4\uFF09"],
    ["v1.0.0-m6", "\u91CC\u7A0B\u7891\u57FA\u7EBF", "2026.06.14", "\u53D1\u5E03\u6536\u53E3\u3001\u56DE\u5F52\u4E0E\u9519\u8BEF\u4F53\u9A8C"],
    ["v1.0.0-rc1", "\u4EA7\u54C1\u57FA\u7EBF", "2026.06.23", "\u5168\u90E8\u5B8C\u6210 + CI/CD \u5168\u901A + Docker \u90E8\u7F72\u53EF\u6F14\u793A"],
  ],
  [18, 20, 16, 46], [3]
));

A.push(H2("4.5 \u53D8\u66F4\u7BA1\u7406\u5DE5\u4F5C\u53CA\u8BC1\u636E"));
A.push(P(
  "\u57FA\u7EBF\u4E2D\u7684\u914D\u7F6E\u9879\u4FEE\u6539\u65F6\uFF0C\u6709\u5BF9\u5E94\u7684\u53D8\u66F4\u5355\uFF0C\u5E76\u901A\u8FC7\u5BA1\u6838\u3002\u914D\u7F6E\u5E93\u63D0\u4EA4\u65E5\u5FD7\u8BB0\u5F55\u51C6\u786E\u3001\u53CA\u65F6\uFF0C\u4E0E\u63D0\u4EA4\u5185\u5BB9\u76F8\u7B26\u3002\u672C\u9879\u76EE\u63D0\u4EA4\u4E86 2 \u4E2A\u771F\u5B9E\u53D8\u66F4\u7533\u8BF7\u5355\u5B9E\u4F8B\uFF1A"
));
A.push(TBL(
  ["\u53D8\u66F4\u5355\u53F7", "\u53D8\u66F4\u5BF9\u8C61", "\u53D8\u66F4\u539F\u56E0", "\u5BA1\u6279\u7ED3\u679C"],
  [
    ["CR-20260613-001", "H5 \u516C\u5F00\u767B\u5F55\u65B9\u5F0F", "\u5FAE\u4FE1\u767B\u5F55\u5EF6\u540E\uFF0C\u9700\u53EF\u843D\u5730\u7684\u767B\u5F55\u65B9\u6848\u963B\u585E M3", "\u6279\u51C6\u5E76\u5B9E\u65BD"],
    ["CR-20260622-002", "\u914D\u7F6E\u7BA1\u7406\u57FA\u7EBF\u5EFA\u7ACB", "\u5EFA\u7ACB CMP \u57FA\u7EBF\uFF0C\u7EB3\u5165 CI/CD \u4E0E Docker \u90E8\u7F72", "\u6279\u51C6\u5E76\u5B9E\u65BD"],
  ],
  [20, 22, 42, 16], [2]
));
A.push(PN("\u63D0\u4EA4\u65E5\u5FD7\u4E0E\u53D8\u66F4\u5355\u5173\u8054\u793A\u4F8B\uFF08\u91C7\u7528 Conventional Commits + ref \u5173\u8054 CR\uFF09\uFF1A"));
A.push(CODE([
  "commit 6fe0d29",
  "refactor(auth): preset security questions with dropdown selection",
  "",
  "\u5173\u8054 CR\uFF1ACR-20260613-001",
  "\u539F\u56E0\uFF1AH5 \u767B\u5F55\u91C7\u7528\u624B\u673A\u53F7+\u5BC6\u7801\uFF0C\u9700\u5B89\u5168\u95EE\u9898\u627E\u56DE\u5BC6\u7801",
]));
A.push(eshot("e03_git_log.png"));
A.push(CAP("\u56FE4-4  \u53D8\u66F4\u7BA1\u7406\u8BC1\u636E\uFF08\u63D0\u4EA4\u65E5\u5FD7\u4E0E CR \u53D8\u66F4\u5355\u5173\u8054\uFF09"));

A.push(H2("4.6 \u7248\u672C\u7BA1\u7406\u5DE5\u4F5C\u53CA\u8BC1\u636E"));
A.push(P(
  "\u7248\u672C\u53D1\u5E03\u4E0E\u914D\u7F6E\u8BA1\u5212\u76F8\u7B26\u5408\u3002\u4EA7\u54C1\u57FA\u7EBF v1.0.0-rc1 \u5DF2\u4E8E 2026-06-23 \u5728 main \u5206\u652F\u6253 tag \u5E76\u63A8\u9001 GitHub\uFF0CCI \u5168\u7EFF\uFF08843/843 \u6D4B\u8BD5\u901A\u8FC7\uFF09\uFF0C\u4E09\u7AEF\u53EF\u8BBF\u95EE\uFF08\u7BA1\u7406\u7AEF 8080 / H5 8081 / API 8082\uFF09\u3002\u90E8\u7F72\u540E\u7684\u8FD0\u884C\u8BC1\u636E\u5982\u4E0B\uFF1A"
));
A.push(shot("02_docker_ps.png"));
A.push(CAP("\u56FE4-5  \u7248\u672C\u53D1\u5E03\u540E\u8FD0\u884C\u8BC1\u636E\uFF08docker compose ps\uFF0C\u56DB\u4E2A\u670D\u52A1\u5747\u4E3A healthy\uFF09"));

// ============================================================
// 第 5 章 持续集成完成情况
// ============================================================
A.push(H1("5 \u6301\u7EED\u96C6\u6210\u5B8C\u6210\u60C5\u51B5\uFF0830\u5206\uFF09"));
A.push(P(
  "\u672C\u5C0F\u7EC4\u6301\u7EED\u96C6\u6210\u5B8C\u6210\u7684\u81EA\u52A8\u5316\u5DE5\u4F5C\u5982\u4E0B\u3002\u9ED8\u8BA4\u8D21\u732E\u5747\u7B49\u3002\u91C7\u7528 Jenkins\uFF08\u4E3B\u529B\uFF09+ GitHub Actions\uFF08\u8865\u5145\uFF09\u53CC\u6D41\u6C34\u7EBF\uFF0CDocker Compose \u5B9E\u73B0\u4E00\u952E\u90E8\u7F72\u3002"
));
A.push(CHECK(true, "\u81EA\u52A8\u7F16\u8BD1\uFF08mvn -B clean compile\uFF0CJenkins Backend Build \u9636\u6BB5\uFF09"));
A.push(CHECK(true, "\u81EA\u52A8\u6253\u5305\uFF08mvn -B package -DskipTests\uFF0C\u4EA7\u51FA\u53EF\u6267\u884C jar\uFF09"));
A.push(CHECK(true, "\u81EA\u52A8\u90E8\u7F72\uFF08docker compose up -d --build --wait\uFF0C\u56DB\u670D\u52A1\u4E00\u952E\u62C9\u8D77\uFF09"));
A.push(CHECK(true, "\u81EA\u52A8\u6D4B\u8BD5\uFF08mvn -B test\uFF08843 \u4E2A\u6D4B\u8BD5\uFF09+ \u524D\u7AEF vitest \u5951\u7EA6\u6D4B\u8BD5\uFF09"));
A.push(CHECK(false, "\u81EA\u52A8\u751F\u6210 allure \u6D4B\u8BD5\u62A5\u544A\uFF08\u672A\u91C7\u7528 allure\uFF0C\u91C7\u7528 JaCoCo \u8986\u76D6\u7387\u62A5\u544A\u4EE3\u66FF\uFF09"));
A.push(CHECK(true, "\u81EA\u52A8\u90AE\u4EF6\u901A\u77E5\uFF08Jenkins Email Extension\uFF0C\u6784\u5EFA\u7ED3\u679C\u901A\u77E5\u7EC4\u5458\uFF09"));
A.push(CHECK(true, "\u5176\u4ED6\uFF1A\u81EA\u52A8\u751F\u6210 JaCoCo \u8986\u76D6\u7387\u62A5\u544A\uFF08class 93% / line 72%\uFF09+ Docker \u955C\u50CF\u591A\u9636\u6BB5\u6784\u5EFA + \u5065\u5EB7\u68C0\u67E5\u5173\u95E8"));
A.push(PN("\u6301\u7EED\u96C6\u6210\u6784\u5EFA\u5386\u53F2\u8BC1\u636E\u5982\u4E0B\uFF1A"));
A.push(eshot("e04_jenkins.png"));
A.push(CAP("\u56FE5-1  Jenkins \u6301\u7EED\u96C6\u6210\u6784\u5EFA\u5386\u53F2\uFF08\u591A\u6B21 SUCCESS \u8BC1\u660E CI \u53EF\u91CD\u590D\uFF09"));

A.push(H2("5.1 \u4EAE\u70B9\u3001\u521B\u65B0\u70B9"));
A.push(LI("1.", "\u53CC CI \u4E92\u8865\uFF1AJenkins \u4E3A\u4E3B\u529B\u6F14\u793A\u6D41\u6C34\u7EBF\uFF08\u7F16\u8BD1\u2192\u6D4B\u8BD5\u2192\u6253\u5305\u2192Docker\u2192\u90E8\u7F72\u2192\u5065\u5EB7\u68C0\u67E5\u2192\u90AE\u4EF6\uFF09\uFF0CGitHub Actions \u4F5C\u4E3A\u8865\u5145\u5B9E\u73B0 push/PR \u89E6\u53D1\u7684\u4E09 job \u5E76\u884C\u9A8C\u8BC1\uFF0C\u4E24\u8005\u4E92\u4E3A\u8865\u5145\u3002"));
A.push(LI("2.", "Docker \u591A\u9636\u6BB5\u6784\u5EFA\uFF1A\u540E\u7AEF\u955C\u50CF\u7528 maven \u7F16\u8BD1 + JRE \u8FD0\u884C\u4E24\u9636\u6BB5\uFF0C\u8FD0\u884C\u955C\u50CF\u66F4\u5C0F\u66F4\u5B89\u5168\uFF1B\u975E root \u7528\u6237\u8FD0\u884C\uFF0CHEALTHCHECK \u5185\u7F6E\u3002"));
A.push(LI("3.", "\u4E00\u952E\u90E8\u7F72\u4E0E\u5065\u5EB7\u5173\u95E8\uFF1Adocker compose up -d --build --wait \u4E00\u6761\u547D\u4EE4\u5B8C\u6210\u955C\u50CF\u6784\u5EFA\u3001\u6570\u636E\u5E93\u521D\u59CB\u5316\u3001\u56DB\u670D\u52A1\u542F\u52A8\u4E0E\u5065\u5EB7\u68C0\u67E5\uFF1Bapi \u4F9D\u8D56 mysql \u7684 healthcheck\uFF0C\u786E\u4FDD\u542F\u52A8\u987A\u5E8F\u3002"));
A.push(LI("4.", "\u5168\u9762\u914D\u7F6E\u7BA1\u7406\u8BC1\u636E\u94FE\uFF1A8 \u4E2A\u57FA\u7EBF tag + 76 \u4E2A\u914D\u7F6E\u9879\u767B\u8BB0 + 2 \u4E2A\u771F\u5B9E\u53D8\u66F4\u5355 + \u7B2C 16 \u5468\u914D\u7F6E\u5BA1\u8BA1\u8BC1\u636E\uFF0C\u5F62\u6210\u53EF\u5BA1\u8BA1\u7684 SCM \u95ED\u73AF\u3002"));

A.push(H2("5.2 \u4E0D\u8DB3"));
A.push(LI("1.", "\u672A\u5F15\u5165 allure \u6D4B\u8BD5\u62A5\u544A\uFF0C\u4EE3\u66FF\u91C7\u7528\u4E86 JaCoCo \u8986\u76D6\u7387\u62A5\u544A\uFF0C\u6D4B\u8BD5\u7ED3\u679C\u7684\u53EF\u89C6\u5316\u7A0B\u5EA6\u6709\u5F85\u52A0\u5F3A\u3002"));
A.push(LI("2.", "\u524D\u7AEF\u8986\u76D6\u7387\u9ED8\u8BA4\u672A\u542F\u7528\uFF08\u5DF2\u5728\u6784\u5EFA\u6307\u5BFC\u4E66\u7ED9\u51FA\u542F\u7528\u6B65\u9AA4\uFF09\uFF0C\u4EC5\u540E\u7AEF\u6709 JaCoCo \u8986\u76D6\u7387\u3002"));
A.push(LI("3.", "Jenkins \u6784\u5EFA\u8FC7\u7A0B\u4E2D\u51FA\u73B0\u8FC7\u51E0\u6B21 FAILURE\uFF08Docker \u6784\u5EFA\u4E0E\u73AF\u5883\u95EE\u9898\uFF09\uFF0C\u5DF2\u4FEE\u590D\u4F46\u8BF4\u660E\u6D41\u6C34\u7EBF\u5065\u58EE\u6027\u4ECD\u9700\u63D0\u5347\u3002"));
A.push(LI("4.", "\u9879\u76EE\u4E3A\u5355\u4EBA\u5B8C\u6210\uFF0C\u7F3A\u4E4F\u591A\u4EBA\u534F\u4F5C\u4E0B\u7684 PR \u8BC4\u5BA1\u4E0E\u5206\u652F\u5408\u5E76\u5B9E\u6218\uFF0C\u5206\u652F\u7B56\u7565\u7684\u534F\u4F5C\u4EF7\u503C\u672A\u5145\u5206\u4F53\u73B0\u3002"));

// ============================================================
// 第 6 章 总结
// ============================================================
A.push(H1("6 \u603B\u7ED3"));
A.push(H2(`6.1 ${ME} \u603B\u7ED3\uFF1A`));
A.push(P(
  "\u5728\u672C\u6B21\u671F\u672B\u8BFE\u7A0B\u8BBE\u8BA1\u4E2D\uFF0C\u6211\u72EC\u7ACB\u5B8C\u6210\u4E86 PetCare O2O \u9879\u76EE\u4ECE\u9700\u6C42\u5230\u4EA7\u54C1\u57FA\u7EBF\u7684\u5168\u6D41\u7A0B\u914D\u7F6E\u7BA1\u7406\u5DE5\u4F5C\u3002\u4E3B\u8981\u5B8C\u6210\u7684\u4EFB\u52A1\u5305\u62EC\uFF1A"
));
A.push(LI("\uFF081\uFF09", "\u4F7F\u7528 Git + GitHub \u5B8C\u6210\u7248\u672C\u63A7\u5236\uFF0C\u5EFA\u7ACB main/develop/phase-* \u5206\u652F\u6A21\u578B\uFF0C\u7D2F\u8BA1 179 \u6B21\u63D0\u4EA4\uFF0C\u5168\u90E8\u9075\u5FAA Conventional Commits \u89C4\u8303\u3002"));
A.push(LI("\uFF082\uFF09", "\u5EFA\u7ACB 8 \u4E2A\u57FA\u7EBF tag\uFF08v1.0.0-fb \u81F3 v1.0.0-rc1\uFF09\uFF0C\u8986\u76D6\u529F\u80FD\u57FA\u7EBF\u3001M1\u2013M6 \u91CC\u7A0B\u7891\u57FA\u7EBF\u4E0E\u4EA7\u54C1\u57FA\u7EBF\uFF0C\u7B26\u5408\u8BED\u4E49\u5316\u7248\u672C\u89C4\u8303\u3002"));
A.push(LI("\uFF083\uFF09", "\u7F16\u5236\u5E76\u843D\u5B9E\u914D\u7F6E\u7BA1\u7406\u8BA1\u5212\uFF0C\u8BC6\u522B 76 \u4E2A\u914D\u7F6E\u9879\uFF0C\u63D0\u4EA4 2 \u4E2A\u771F\u5B9E\u53D8\u66F4\u7533\u8BF7\u5355\u5B9E\u4F8B\uFF0C\u5E76\u5B8C\u6210\u7B2C 16 \u5468\u914D\u7F6E\u5BA1\u8BA1\u3002"));
A.push(LI("\uFF084\uFF09", "\u642D\u5EFA Jenkins + GitHub Actions \u53CC CI \u6D41\u6C34\u7EBF\uFF0C\u5B9E\u73B0\u81EA\u52A8\u7F16\u8BC1\u3001\u6D4B\u8BD5\uFF08843 \u4E2A\u6D4B\u8BD5\uFF09\u3001\u6253\u5305\u3001Docker \u955C\u50CF\u6784\u5EFA\u3001\u4E00\u952E\u90E8\u7F72\u4E0E\u90AE\u4EF6\u901A\u77E5\u7684\u5168\u81EA\u52A8\u5316\u95ED\u73AF\u3002"));
A.push(LI("\uFF085\uFF09", "\u91C7\u7528 Docker Compose \u5B9E\u73B0\u5BB9\u5668\u5316\u4E00\u952E\u90E8\u7F72\uFF0C\u5B8C\u6210\u540E\u7AEF API\u3001\u7BA1\u7406\u7AEF\u3001\u7528\u6237\u7AEF H5\u3001MySQL \u56DB\u670D\u52A1\u5747\u53EF\u8FD0\u884C\u4E14\u5065\u5EB7\u68C0\u67E5\u901A\u8FC7\u3002"));
A.push(P(
  "\u901A\u8FC7\u672C\u6B21\u8BFE\u7A0B\u8BBE\u8BA1\uFF0C\u6211\u6DF1\u5165\u7406\u89E3\u4E86\u8F6F\u4EF6\u914D\u7F6E\u7BA1\u7406\u5728\u4FDD\u8BC1\u8F6F\u4EF6\u8D28\u91CF\u4E0E\u53EF\u8FFD\u6EAF\u6027\u65B9\u9762\u7684\u4EF7\u503C\uFF0C\u638C\u63E1\u4E86 Git \u5206\u652F\u7B56\u7565\u3001\u57FA\u7EBF\u4E0E\u53D8\u66F4\u63A7\u5236\u3001\u6301\u7EED\u96C6\u6210\u4E0E\u5BB9\u5668\u5316\u90E8\u7F72\u7684\u5B9E\u64CD\u80FD\u529B\u3002"
));

// ============================================================
// 参考文献
// ============================================================
A.push(H1("\u53C2\u8003\u6587\u732E"));
A.push(LI("[1]", "IEEE Std 828-2012. Configuration Management in Systems and Software Engineering [S]. IEEE, 2012."));
A.push(LI("[2]", "GB/T 11457-2006. \u4FE1\u606F\u6280\u672F \u8F6F\u4EF6\u5DE5\u7A0B\u672F\u8BED [S]. \u4E2D\u534E\u4EBA\u6C11\u5171\u548C\u56FD\u56FD\u5BB6\u6807\u51C6, 2006."));
A.push(LI("[3]", "ISO 10007:2017. Quality management systems\u2014Guidelines for configuration management [S]. ISO, 2017."));
A.push(LI("[4]", "\u5468\u660E. \u8F6F\u4EF6\u914D\u7F6E\u7BA1\u7406\u539F\u7406\u4E0E\u5B9E\u8DF5 [M]. \u5317\u4EAC\uFF1A\u6E05\u534E\u5927\u5B66\u51FA\u7248\u793E, 2020."));
A.push(LI("[5]", "Spring Team. Spring Boot Reference Documentation 3.3.x [EB/OL]. https://docs.spring.io/spring-boot/, 2026-06-23/2026-06-26."));
A.push(LI("[6]", "Conventional Commits 1.0. Conventional Commits Specification [EB/OL]. https://www.conventionalcommits.org/, 2026-06-26."));
A.push(LI("[7]", "Docker Inc. Docker Compose Documentation [EB/OL]. https://docs.docker.com/compose/, 2026-06-26."));

// ============================================================
// 封面
// ============================================================
function buildCover() {
  const C = [];
  C.push(BLANK(28));
  C.push(new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 0, after: 240, line: Math.ceil(20 * 23) },
    children: [R("\u5DE5\u5B66\u9662", { bold: true, size: 36 })],
  }));
  C.push(new Paragraph({
    alignment: AlignmentType.CENTER, spacing: { after: 360 },
    children: [R("\u8BFE\u7A0B\u8BBE\u8BA1\u62A5\u544A", { bold: true, size: TITLE })],
  }));
  C.push(new Paragraph({
    alignment: AlignmentType.CENTER, spacing: { after: 160 },
    children: [R("\u8BFE\u7A0B\u540D\u79F0\uFF1A\u8F6F\u4EF6\u914D\u7F6E\u7BA1\u7406", { size: 30 })],
  }));
  C.push(new Paragraph({
    alignment: AlignmentType.CENTER, spacing: { after: 360 },
    children: [R("\u9879\u76EE\u540D\u79F0\uFF1APetCare O2O", { bold: true, size: 30 })],
  }));

  C.push(BLANK(20));
  // 成员表
  const mH = new TableRow({ tableHeader: true, cantSplit: true, children: [
    CELL("\u5B66\u53F7", { bold: true, width: 14 }),
    CELL("\u59D3\u540D", { bold: true, width: 10 }),
    CELL("\u6587\u6863\u8D21\u732E\u5EA6\uFF08\u5747\u503C\u4E3A1\uFF09", { bold: true, width: 16 }),
    CELL("\u5F00\u53D1\u4E0E\u6301\u7EED\u96C6\u6210\u5DE5\u4F5C", { bold: true, width: 50 }),
    CELL("\u8BFE\u7A0B\u6210\u7EE9", { bold: true, width: 10 }),
  ]});
  const mR = new TableRow({ cantSplit: true, children: [
    CELL(SID, { width: 14 }),
    CELL(ME, { bold: true, width: 10 }),
    CELL("1.0", { width: 16 }),
    CELL("\u72EC\u7ACB\u5B8C\u6210\u5168\u90E8\u914D\u7F6E\u7BA1\u7406\u4E0E\u6301\u7EED\u96C6\u6210\u5DE5\u4F5C\uFF1A\u7248\u672C\u5E93\u4E0E\u5206\u652F\u7BA1\u7406\u3001\u57FA\u7EBF\u4E0E\u53D8\u66F4\u7BA1\u7406\u3001Jenkins \u6D41\u6C34\u7EBF\u7F16\u5199\u4E0E\u81EA\u52A8\u7F16\u8BD1/\u6D4B\u8BD5/\u6253\u5305/\u90E8\u7F72/\u90AE\u4EF6\u901A\u77E5", { width: 50, align: AlignmentType.LEFT }),
    CELL("", { width: 10 }),
  ]});
  C.push(new Table({ width: { size: 100, type: WidthType.PERCENTAGE }, rows: [mH, mR] }));

  C.push(BLANK(20));
  C.push(new Paragraph({
    alignment: AlignmentType.RIGHT, spacing: { before: 240 },
    children: [R("2026 \u5E74 6 \u6708 26 \u65E5", { size: BODY })],
  }));
  return C;
}

// ============================================================
// 目录
// ============================================================
function buildTOC() {
  return [
    new Paragraph({
      alignment: AlignmentType.CENTER, spacing: { before: 0, after: 200 },
      children: [R("\u76EE\u3000\u5F55", { bold: true, size: H1S })],
    }),
    new TableOfContents("\u76EE\u5F55", {
      hyperlink: true, headingStyleRange: "1-2",
      stylesWithLevels: [new StyleLevel("Heading1", 1), new StyleLevel("Heading2", 2)],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER, spacing: { before: 120 },
      children: [R("\uFF08\u53F3\u952E\u76EE\u5F55 \u2192 \u201C\u66F4\u65B0\u57DF\u201D \u53EF\u5237\u65B0\u9875\u7801\uFF09", { italics: true, size: 20, color: "808080" })],
    }),
  ];
}

// ============================================================
// 文档装配
// ============================================================
const doc = new Document({
  creator: ME,
  title: "PetCare O2O \u8F6F\u4EF6\u914D\u7F6E\u7BA1\u7406\u8BFE\u7A0B\u8BBE\u8BA1\u62A5\u544A",
  styles: {
    default: {
      document: {
        run: { font: { ascii: EN, eastAsia: CN }, size: BODY, color: COLOR },
        paragraph: { spacing: { line: LINE } },
      },
    },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { bold: true, size: H1S, color: COLOR, font: { ascii: EN, eastAsia: CN } },
        paragraph: { spacing: { before: 360, after: 200, line: 400 } } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { bold: true, size: H2S, color: COLOR, font: { ascii: EN, eastAsia: CN } },
        paragraph: { spacing: { before: 280, after: 140, line: 360 } } },
    ],
  },
  sections: [
    { properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, bottom: 1440, left: 1701, right: 1417 } } },
      children: buildCover() },
    { properties: { type: SectionType.NEXT_PAGE,
        page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, bottom: 1440, left: 1701, right: 1417 },
          pageNumbers: { start: 1, formatType: NumberFormat.UPPER_ROMAN } } },
      footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
        children: [new TextRun({ children: [PageNumber.CURRENT], size: 20, color: COLOR, font: { ascii: EN, eastAsia: CN } })] })] }) },
      children: buildTOC() },
    { properties: { type: SectionType.NEXT_PAGE,
        page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, bottom: 1440, left: 1701, right: 1417 },
          pageNumbers: { start: 1, formatType: NumberFormat.DECIMAL } } },
      footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
        children: [new TextRun({ children: [PageNumber.CURRENT], size: 20, color: COLOR, font: { ascii: EN, eastAsia: CN } })] })] }) },
      children: A },
  ],
});

const outDir = "F:/trae code/petcare-o2o/docs";
const out = outDir + "/PetCare O2O\u8F6F\u4EF6\u914D\u7F6E\u7BA1\u7406\u8BFE\u7A0B\u8BBE\u8BA1\u62A5\u544A.docx";
Packer.toBuffer(doc).then((buf) => {
  try {
    fs.writeFileSync(out, buf);
    console.log("OK ->", out);
  } catch (e) {
    if (e.code === "EBUSY" || e.code === "EPERM") {
      const tmp = outDir + "/PetCare O2O\u8F6F\u4EF6\u914D\u7F6E\u7BA1\u7406\u8BFE\u7A0B\u8BBE\u8BA1\u62A5\u544A_\u65B0.docx";
      fs.writeFileSync(tmp, buf);
      console.log("OK (\u4E3B\u6587\u4EF6\u88AB\u5360\u7528\uFF0C\u5DF2\u5199\u5165\u4E34\u65F6\u6587\u4EF6) ->", tmp);
    } else { throw e; }
  }
});
