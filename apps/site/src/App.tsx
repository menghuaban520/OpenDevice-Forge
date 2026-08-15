import {
  ArrowRight, Check, ChevronDown, CircleAlert, Cpu, Download, Eye, GitFork,
  Layers3, LockKeyhole, PlugZap, RefreshCw, RotateCcw, ShieldCheck,
  SlidersHorizontal, Smartphone, Usb,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { detectBrowserPlatform, preferredDownload, type Platform } from "./platform";

const platforms: Array<{ id: Exclude<Platform, "other">; label: string }> = [
  { id: "macos", label: "macOS" },
  { id: "windows", label: "Windows" },
];

const features = [
  { icon: PlugZap, index: "01", title: "能力由插件组成", copy: "检测、报告、模型节点和远程服务各自独立。需要什么就启用什么，侧边栏和工作区也能重新编排。" },
  { icon: RotateCcw, index: "02", title: "每次变化都能撤回", copy: "安装前先检查权限与兼容性；启用、停用、更新、卸载都有记录，故障时可回滚或进入安全模式。" },
  { icon: Eye, index: "03", title: "证据来源写清楚", copy: "实机读取、公开资料和演示数据分开标记。报告不会把推测伪装成手机真实状态。" },
];

export function App() {
  const [platform, setPlatform] = useState<Platform>("other");
  const [platformMenuOpen, setPlatformMenuOpen] = useState(false);

  useEffect(() => setPlatform(detectBrowserPlatform()), []);
  const download = useMemo(() => preferredDownload(platform), [platform]);

  return (
    <div className="site-shell">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="OpenDevice Forge 首页">
          <img src="/app-mark.svg" alt="" /><span>OpenDevice Forge</span>
        </a>
        <nav aria-label="主导航"><a href="#product">产品</a><a href="#workflow">使用方式</a><a href="#status">当前状态</a></nav>
        <a className="header-source" href="#status"><GitFork aria-hidden="true" />开源计划</a>
      </header>

      <main id="top">
        <section className="hero">
          <div className="hero-copy">
            <div className="eyebrow"><span />只读 MVP · 插件优先</div>
            <h1>让旧安卓手机，<br />成为可配置的设备节点。</h1>
            <p className="hero-lead">一套面向 Windows 与 macOS 的桌面工作台。先读懂设备，再按需装入检测、报告、模型与远程服务插件。</p>
            <div className="download-group" aria-label="下载选择">
              <button className="download-main" type="button" disabled>
                <Download aria-hidden="true" />
                <span><strong>{download.label} 版本</strong><small>{download.reason}</small></span>
              </button>
              <div className="platform-picker">
                <button className="platform-toggle" type="button" aria-label="选择其他平台" aria-expanded={platformMenuOpen} onClick={() => setPlatformMenuOpen((open) => !open)}>
                  <ChevronDown aria-hidden="true" />
                </button>
                {platformMenuOpen && <div className="platform-menu">
                  {platforms.map((item) => <button key={item.id} type="button" onClick={() => { setPlatform(item.id); setPlatformMenuOpen(false); }}>
                    <span>{item.label}</span>{platform === item.id && <Check aria-hidden="true" />}
                  </button>)}
                </div>}
              </div>
            </div>
            <p className="release-note">首个公开版正在做真实设备与双平台验证，完成前不提供假下载链接。</p>
          </div>

          <div className="hero-product" aria-label="OpenDevice Forge 插件市场界面预览">
            <div className="product-caption"><span>桌面端实装画面</span><span className="readonly-pill"><LockKeyhole aria-hidden="true" />只读边界</span></div>
            <div className="product-window">
              <div className="window-chrome"><i /><i /><i /><span>OpenDevice Forge</span></div>
              <img src="/product/plugin-market.png" alt="OpenDevice Forge 插件市场实装截图" />
            </div>
          </div>
        </section>

        <section className="principle-strip" aria-label="产品原则">
          <div><ShieldCheck aria-hidden="true" /><span><strong>默认只读</strong>首发版不执行 Root、解锁或任意命令</span></div>
          <div><Layers3 aria-hidden="true" /><span><strong>微内核</strong>稳定底座管理插件、权限和恢复</span></div>
          <div><RefreshCw aria-hidden="true" /><span><strong>可恢复</strong>配置、布局和插件状态都留有退路</span></div>
        </section>

        <section className="section product-section" id="product">
          <div className="section-heading"><span>一个小内核，多个真实用途</span><h2>不是把所有功能焊死在一起。</h2><p>OpenDevice Forge 只把设备发现、插件生命周期、权限、布局和审计留在核心里。其余能力都可以独立演进。</p></div>
          <div className="feature-list">
            {features.map(({ icon: Icon, index, title, copy }) => <article className="feature-row" key={index}><span className="feature-index">{index}</span><Icon aria-hidden="true" /><h3>{title}</h3><p>{copy}</p></article>)}
          </div>
        </section>

        <section className="section workflow-section" id="workflow">
          <div className="section-heading compact"><span>第一次使用</span><h2>一根数据线，先从看清设备开始。</h2></div>
          <ol className="workflow-list">
            <li><div className="step-icon"><Usb aria-hidden="true" /></div><span>01</span><h3>连接</h3><p>在安卓手机中允许 USB 调试，并通过数据线连接电脑。</p></li>
            <li><div className="step-icon"><Smartphone aria-hidden="true" /></div><span>02</span><h3>读取</h3><p>桌面端只调用预先限定的 ADB 读取指令，生成设备事实。</p></li>
            <li><div className="step-icon"><SlidersHorizontal aria-hidden="true" /></div><span>03</span><h3>配置</h3><p>查看插件权限与兼容性，再决定启用哪些能力和入口。</p></li>
            <li><div className="step-icon"><Cpu aria-hidden="true" /></div><span>04</span><h3>扩展</h3><p>后续再把模型节点、远程访问等能力作为独立插件接入。</p></li>
          </ol>
          <div className="requirements"><strong>需要准备</strong><span>Windows 或 macOS 电脑</span><span>安卓手机与可传输数据的 USB 线</span><span>可选：官方 Android Platform Tools</span></div>
        </section>

        <section className="section status-section" id="status">
          <div className="status-copy"><span className="status-kicker">MVP 状态 · 2026.08</span><h2>诚实地停在“可本地验证”。</h2><p>桌面界面、插件微内核、演示设备与只读 ADB 适配器已经落地。当前没有公开发行包，也没有把尚未做过的 Windows 实机、签名安装和华为真机测试写成“已支持”。</p><a href="#top">查看桌面端预览 <ArrowRight aria-hidden="true" /></a></div>
          <div className="status-ledger">
            <div className="ledger-row ready"><Check aria-hidden="true" /><span>已完成</span><strong>插件生命周期与布局配置</strong></div>
            <div className="ledger-row ready"><Check aria-hidden="true" /><span>已完成</span><strong>macOS 本地构建链路</strong></div>
            <div className="ledger-row pending"><CircleAlert aria-hidden="true" /><span>待验证</span><strong>Windows 真实运行与签名</strong></div>
            <div className="ledger-row pending"><CircleAlert aria-hidden="true" /><span>待验证</span><strong>HUAWEI nova 7 SE 5G 乐活版实机</strong></div>
          </div>
        </section>
      </main>

      <footer><div className="brand footer-brand"><img src="/app-mark.svg" alt="" /><span>OpenDevice Forge</span></div><p>旧设备的新入口。所有高权限能力，都应先讲清风险与退路。</p><span>本地 MVP · 尚未公开发布</span></footer>
    </div>
  );
}
