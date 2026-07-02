// 截图脚本：为《构建和部署指南》补齐 4 张真实运行截图
// 复用系统 Chrome（puppeteer-core），不下载 Chromium
// 产出：
//   01_hostname.png        — 计算机名（hostname 命令输出）
//   02_docker_ps.png       — docker compose ps 服务健康状态
//   03_admin_dashboard.png — 管理端登录后主界面
//   04_h5_home.png         — 用户端 H5 首页

const puppeteer = require("puppeteer-core");
const { execSync } = require("child_process");
const fs = require("fs");

// 系统 Chrome 路径
const CHROME_PATH =
  "C:/Program Files/Google/Chrome/Application/chrome.exe";

// 端点
const ADMIN = "http://localhost:8080";
const H5 = "http://localhost:8081";

// 把命令输出渲染成"终端风格"PNG
async function shotTerminal(page, title, output, file) {
  const esc = (s) =>
    String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  const html = `<!doctype html><html><head><meta charset="utf-8">
<style>
  body{margin:0;background:#0c0c0c;font-family:'Cascadia Mono','Consolas','Courier New',monospace;}
  .bar{background:#2d2d30;color:#e6e6e6;padding:8px 14px;font-size:13px;
       border-bottom:1px solid #3e3e42;display:flex;align-items:center;gap:8px;}
  .dot{width:11px;height:11px;border-radius:50%;display:inline-block;}
  .r{background:#ff5f57}.y{background:#febc2e}.g{background:#28c840}
  .t{color:#999;margin-left:8px;}
  pre{margin:0;padding:16px 18px;color:#e6e6e6;font-size:14px;line-height:1.55;
      white-space:pre;overflow-x:auto;}
  .ok{color:#4ec9b0}.warn{color:#dcdcaa}.head{color:#569cd6}
</style></head><body>
  <div class="bar">
    <span class="dot r"></span><span class="dot y"></span><span class="dot g"></span>
    <span class="t">${esc(title)} — 命令提示符</span>
  </div>
  <pre>${esc(output)}</pre>
</body></html>`;
  await page.setContent(html, { waitUntil: "networkidle0" });
  // 截取 body 紧贴内容区域
  await page.screenshot({ path: file, omitBackground: false });
  console.log("saved:", file);
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: "new",
    args: ["--no-sandbox", "--disable-gpu", "--force-device-scale-factor=1.5"],
  });

  try {
    // ---------- 图1：hostname ----------
    let out;
    try {
      out = execSync("hostname", { encoding: "utf8" }).trim();
    } catch {
      out = execSync("powershell -NoProfile -Command \"$env:COMPUTERNAME\"", {
        encoding: "utf8",
      }).trim();
    }
    const hostBlock =
      `C:\\Users\\27269>hostname\n` +
      `${out}\n\n` +
      `C:\\Users\\27269>echo 计算机名: %COMPUTERNAME%\n` +
      `计算机名: ${out}\n`;
    const page = await browser.newPage();
    await page.setViewport({ width: 900, height: 360 });
    await shotTerminal(page, "hostname", hostBlock, "01_hostname.png");
    await page.close();

    // ---------- 图2：docker compose ps ----------
    let psOut;
    try {
      psOut = execSync(
        'cd /d "F:\\trae code\\petcare-o2o" && docker compose ps',
        { encoding: "utf8", stdio: ["pipe", "pipe", "pipe"] }
      ).trim();
    } catch (e) {
      // Git Bash 环境兜底
      psOut = execSync('docker compose ps', { encoding: "utf8" }).trim();
    }
    const psBlock =
      `petcare-o2o> docker compose ps\n` +
      psOut +
      `\n\npetcare-o2o> `;
    const page2 = await browser.newPage();
    await page2.setViewport({ width: 1280, height: 520 });
    await shotTerminal(page2, "docker compose ps", psBlock, "02_docker_ps.png");
    await page2.close();

    // ---------- 图3：管理端登录后主界面 ----------
    const admin = await browser.newPage();
    await admin.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });
    await admin.goto(`${ADMIN}/login`, { waitUntil: "networkidle0", timeout: 30000 });
    await new Promise((r) => setTimeout(r, 1200));

    // 填写登录表单（Element Plus 输入框）
    const userInput = await admin.$('input[placeholder="用户名"], input[autocomplete="username"]');
    if (userInput) await userInput.type("admin", { delay: 30 });
    const passInput = await admin.$('input[type="password"], input[placeholder="密码"]');
    if (passInput) await passInput.type("admin123456", { delay: 30 });
    await new Promise((r) => setTimeout(r, 500));

    // 点击登录按钮（匹配含"登录"文本的按钮）
    const btns = await admin.$$("button");
    let clicked = false;
    for (const b of btns) {
      const txt = (await admin.evaluate((el) => el.textContent, b)) || "";
      if (/登\s*录|Login/i.test(txt)) {
        await b.click();
        clicked = true;
        break;
      }
    }
    if (!clicked) {
      await admin.keyboard.press("Enter");
    }
    // 等待跳转离开 /login
    await admin
      .waitForFunction(() => !location.pathname.includes("login"), { timeout: 20000 })
      .catch(() => {});
    await new Promise((r) => setTimeout(r, 2500));
    await admin.screenshot({ path: "03_admin_dashboard.png" });
    console.log("saved: 03_admin_dashboard.png");
    await admin.close();

    // ---------- 图4：H5 首页（移动端尺寸） ----------
    const h5 = await browser.newPage();
    await h5.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true });
    await h5.goto(H5, { waitUntil: "networkidle0", timeout: 30000 });
    await new Promise((r) => setTimeout(r, 3000));
    await h5.screenshot({ path: "04_h5_home.png" });
    console.log("saved: 04_h5_home.png");
    await h5.close();
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error("CAPTURE FAILED:", e);
  process.exit(1);
});
