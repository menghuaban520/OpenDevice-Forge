import { useState } from "react";
import {
  BrainCircuit,
  ChevronRight,
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
  onAction: (action: PluginRegistryAction) => void;
  onEditLayout: () => void;
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

export function PluginDetail({ manifest, installed, onAction, onEditLayout }: PluginDetailProps) {
  const Icon = iconFor(manifest.id);
  const isRootGuide = manifest.id === "dev.opendevice.root-guide";
  const [saved, setSaved] = useState(false);
  return (
    <div className="plugin-detail-grid">
      <section className="plugin-detail-main">
        <header className="plugin-detail-header">
          <div className="plugin-icon-large"><Icon /></div>
          <div><h2>{manifest.name}</h2><p>{manifest.summary}</p></div>
          <div className="plugin-lifecycle">
            {installed ? (
              <>
                <label className="switch-label">
                  <span className="sr-only">启用 {manifest.name}</span>
                  <input
                    type="checkbox"
                    role="switch"
                    aria-label={`启用 ${manifest.name}`}
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
              </>
            ) : (
              <button
                className="primary-button"
                type="button"
                aria-label={`安装 ${manifest.name}`}
                onClick={() => onAction({ type: "install", manifest })}
              >安装插件</button>
            )}
          </div>
        </header>

        <dl className="plugin-meta">
          <div><dt>版本</dt><dd>{manifest.version}</dd></div>
          <div><dt>来源</dt><dd>{sourceText(manifest)}</dd></div>
          <div><dt>执行方式</dt><dd>{manifest.execution === "declarative" ? "声明式清单" : "原生插件"}</dd></div>
          <div><dt>适用范围</dt><dd>{manifest.audience === "advanced" ? "高级用户" : "所有用户"}</dd></div>
          <div><dt>风险</dt><dd><span className={`risk-dot ${manifest.risk}`} />{riskText(manifest)}</dd></div>
        </dl>

        {isRootGuide ? (
          <div className="root-boundary"><CircleAlert /><div><strong>{manifest.summary}</strong><p>本版本没有解锁、刷写、提权或安装入口；只整理条件、风险与恢复资料。</p></div></div>
        ) : null}

        <div className="detail-section">
          <div className="section-heading"><h3>显示位置</h3><button type="button" onClick={onEditLayout}>编辑全部插件布局</button></div>
          {manifest.contributes.map((contribution) => (
            <div className="contribution-row" key={contribution.id}>
              <CircleCheck />
              <div><strong>{contribution.label}</strong><span>{contribution.type}</span></div>
              <span>{contribution.defaultPlacement ?? "由插件决定"}</span>
            </div>
          ))}
        </div>

        <div className="detail-section permission-section">
          <h3>权限</h3>
          {manifest.permissions.length === 0 ? <p>无需设备权限</p> : manifest.permissions.map((permission) => (
            <div className="permission-row" key={permission}><Shield /><span>{permission}</span><strong>安装前可见</strong></div>
          ))}
          <p className="permission-note">布局编辑不会增加插件权限</p>
        </div>

        {installed && !manifest.protected ? (
          <div className="plugin-secondary-actions">
            <button type="button" onClick={() => onAction({ type: "rollback", pluginId: manifest.id })}><RotateCcw />回退版本</button>
            <button className="danger-text" type="button" onClick={() => onAction({ type: "uninstall", pluginId: manifest.id })}>卸载插件</button>
          </div>
        ) : null}
      </section>

      <aside className="plugin-config-rail">
        <div className="config-title"><h2>{manifest.name}设置</h2><select aria-label="配置范围"><option>仅此设备</option><option>所有设备</option></select></div>
        <label>配置预设<select><option>日常使用</option><option>低功耗</option><option>实验模式</option></select></label>
        <label>服务端口<input type="number" defaultValue={11434} min={1024} max={65535} /></label>
        <label>访问范围<select><option>仅本机</option><option>仅局域网</option></select></label>
        <div className="config-toggle"><span>开机自动启动</span><span className="mini-switch" /></div>
        <div className="config-toggle"><span>启用 API 密钥</span><span className="mini-switch on" /></div>
        <div className="config-disclosure"><strong>将读取</strong><span>设备型号、内存、存储、温度等</span><ChevronRight /></div>
        <div className="config-disclosure"><strong>将修改</strong><span>{isRootGuide ? "无；仅显示资料" : "仅保存本机配置"}</span><ChevronRight /></div>
        <div className="config-disclosure"><strong>回退方式</strong><span>停用插件并恢复上一次配置</span><ChevronRight /></div>
        <div className="config-actions"><button className="primary-button" type="button" onClick={() => setSaved(true)}>保存设置</button><button type="button" disabled>部署到手机</button></div>
        <p className="config-hint">{saved ? "设置已保存到本机" : "需要连接真机，且本里程碑不会安装手机端组件"}</p>
      </aside>
    </div>
  );
}
