import {
  BrainCircuit,
  CircleAlert,
  CircleCheck,
  Cloud,
  FileOutput,
  RotateCcw,
  Shield,
  Stethoscope,
} from "lucide-react";
import type { InstalledPlugin, PluginManifest, PluginRegistryAction } from "@opendevice/core";

interface PluginDetailProps {
  manifest: PluginManifest;
  installed?: InstalledPlugin;
  runtimeAvailable: boolean;
  safeMode: boolean;
  onAction: (action: PluginRegistryAction) => void;
  onOpen: () => void;
}

const iconFor = (id: string) => {
  if (id.includes("ai-readiness")) return BrainCircuit;
  if (id.includes("remote")) return Cloud;
  if (id.includes("report")) return FileOutput;
  if (id.includes("root")) return Shield;
  return Stethoscope;
};

const sourceText = (manifest: PluginManifest) => {
  if (manifest.source.kind === "official") return "OpenDevice Forge 官方目录";
  if (manifest.source.kind === "github") return `GitHub · ${manifest.source.repository}`;
  if (manifest.source.kind === "community") return `社区目录 · ${manifest.source.catalog}`;
  return "本地高级包";
};

const riskText = (manifest: PluginManifest) =>
  manifest.risk === "low" ? "低风险" : manifest.risk === "medium" ? "中等风险" : "高风险";

const contributionText: Record<string, string> = {
  navigation: "可固定为入口",
  overviewAction: "设备页快捷动作",
  overviewSection: "设备页信息区",
  deviceProbe: "只读设备检测",
  configuration: "提供专属设置",
  workflow: "可执行工作流",
  service: "本机服务",
  companion: "手机端组件",
  reportSection: "报告内容",
  contextAction: "设备快捷动作",
};

export function PluginDetail({
  manifest,
  installed,
  runtimeAvailable,
  safeMode,
  onAction,
  onOpen,
}: PluginDetailProps) {
  const Icon = iconFor(manifest.id);
  const isRootGuide = manifest.id === "dev.opendevice.root-guide";
  const canOpen = Boolean(installed?.enabled && !safeMode);

  return (
    <div className="plugin-detail-grid">
      <section className="plugin-detail-main">
        <header className="plugin-detail-header">
          <div className="plugin-icon-large"><Icon /></div>
          <div><h2>{manifest.name}</h2><p>{manifest.summary}</p></div>
          <div className="plugin-lifecycle">
            {!runtimeAvailable ? (
              <span className="runtime-unavailable">尚未接入运行器</span>
            ) : installed ? (
              <>
                <label className="switch-label">
                  <span className="sr-only">启用{manifest.name}</span>
                  <input
                    type="checkbox"
                    role="switch"
                    aria-label={`启用${manifest.name}`}
                    checked={installed.enabled}
                    disabled={manifest.protected}
                    onChange={() => onAction({
                      type: installed.enabled ? "disable" : "enable",
                      pluginId: manifest.id,
                    })}
                  />
                  <span className="switch-track" />
                </label>
                <strong className={installed.enabled ? "state-enabled" : "state-disabled"}>
                  {installed.enabled ? "已启用" : "已停用"}
                </strong>
                <button
                  className="primary-button"
                  type="button"
                  aria-label={`打开${manifest.name}`}
                  disabled={!canOpen}
                  onClick={onOpen}
                >打开插件</button>
              </>
            ) : (
              <button
                className="primary-button"
                type="button"
                aria-label={`安装${manifest.name}`}
                onClick={() => onAction({ type: "install", manifest })}
              >安装插件</button>
            )}
          </div>
        </header>

        <dl className="plugin-meta">
          <div><dt>版本</dt><dd>{manifest.version}</dd></div>
          <div><dt>来源</dt><dd>{sourceText(manifest)}</dd></div>
          <div><dt>运行方式</dt><dd>{manifest.execution === "declarative" ? "受控声明式" : "原生插件"}</dd></div>
          <div><dt>适用用户</dt><dd>{manifest.audience === "advanced" ? "高级用户" : "所有用户"}</dd></div>
          <div><dt>风险</dt><dd><span className={`risk-dot ${manifest.risk}`} />{riskText(manifest)}</dd></div>
        </dl>

        {isRootGuide ? (
          <div className="root-boundary"><CircleAlert /><div><strong>{manifest.summary}</strong><p>本版本没有解锁、刷写、提权或安装入口；只整理条件、风险与恢复资料。</p></div></div>
        ) : null}

        <div className="detail-section">
          <div className="section-heading"><h3>这个插件能做什么</h3></div>
          {manifest.contributes.map((contribution) => (
            <div className="contribution-row" key={contribution.id}>
              <CircleCheck />
              <div><strong>{contribution.label}</strong><span>{contributionText[contribution.type] ?? contribution.type}</span></div>
              <span>{contribution.defaultPlacement ? "默认显示" : "按需使用"}</span>
            </div>
          ))}
        </div>

        <div className="detail-section permission-section">
          <h3>安装前会请求的权限</h3>
          {manifest.permissions.length === 0 ? <p>无需设备权限</p> : manifest.permissions.map((permission) => (
            <div className="permission-row" key={permission}><Shield /><span>{permission}</span><strong>安装前可见</strong></div>
          ))}
        </div>

        {installed && !manifest.protected ? (
          <div className="plugin-secondary-actions">
            {installed.previousVersions.length > 0 ? <button type="button" onClick={() => onAction({ type: "rollback", pluginId: manifest.id })}><RotateCcw />回退版本</button> : null}
            <button className="danger-text" type="button" onClick={() => onAction({ type: "uninstall", pluginId: manifest.id })}>卸载插件</button>
          </div>
        ) : null}
      </section>
    </div>
  );
}
