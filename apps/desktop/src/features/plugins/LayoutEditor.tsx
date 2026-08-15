import { useMemo, useState } from "react";
import {
  Cloud,
  FileText,
  GripVertical,
  Home,
  Info,
  Menu,
  MousePointer2,
  RotateCcw,
  Save,
} from "lucide-react";
import {
  setPlacement,
  movePlacement,
  visiblePlugins,
  type DeviceSnapshot,
  type LayoutProfile,
  type PlacementSlot,
  type PluginRegistryState,
} from "@opendevice/core";
import { deviceDisplayName } from "../../components/AppShell";

interface LayoutEditorProps {
  snapshot: DeviceSnapshot;
  profiles: LayoutProfile[];
  registry: PluginRegistryState;
  onProfilesChange: (profiles: LayoutProfile[]) => void;
  onBack: () => void;
}

const placementOptions: Array<{ slot: PlacementSlot; label: string; detail: string; icon: typeof Menu }> = [
  { slot: "sidebar", label: "侧边栏入口", detail: "在设备侧边栏显示插件入口", icon: Menu },
  { slot: "shortcut", label: "设备总览快捷动作", detail: "在设备总览顶部显示快捷动作", icon: Home },
  { slot: "overview", label: "设备总览信息区", detail: "在设备总览页面信息区显示插件内容", icon: Info },
  { slot: "service", label: "远程服务列表", detail: "在远程服务页面显示插件入口", icon: Cloud },
  { slot: "report", label: "报告内容", detail: "在导出报告中包含插件相关内容", icon: FileText },
  { slot: "context", label: "右键快捷菜单", detail: "在设备列表右键菜单中显示", icon: MousePointer2 },
];

export function LayoutEditor({ snapshot, profiles, registry, onProfilesChange, onBack }: LayoutEditorProps) {
  const [draftProfiles, setDraftProfiles] = useState(profiles);
  const [activeId, setActiveId] = useState("default");
  const [dirty, setDirty] = useState(0);
  const [notice, setNotice] = useState("");
  const active = draftProfiles.find((profile) => profile.id === activeId) ?? draftProfiles[0];
  const ai = registry.plugins["dev.opendevice.ai-readiness"];
  const enabled = Object.values(registry.plugins).filter((plugin) => plugin.enabled && !plugin.manifest.protected);
  const sidebarIds = active ? visiblePlugins(active, "sidebar") : [];
  const profileOptions = useMemo(() => draftProfiles.map((profile) => ({ id: profile.id, name: profile.name })), [draftProfiles]);
  const displayName = deviceDisplayName(snapshot);
  const productCode = snapshot.productName.value ?? (snapshot.connection === "ready" ? "Android 设备" : "等待连接");

  if (!active || !ai) return null;

  const toggle = (slot: PlacementSlot, visible: boolean) => {
    const updated = setPlacement(active, slot, ai.manifest.id, { visible });
    setDraftProfiles((current) => current.map((profile) => profile.id === active.id ? updated : profile));
    setDirty((count) => count + 1);
    setNotice("");
  };

  const restore = () => {
    const defaults = createAcceptedLayoutProfiles().find((profile) => profile.id === active.id);
    if (defaults) setDraftProfiles((current) => current.map((profile) => profile.id === active.id ? defaults : profile));
    setDirty(0);
    setNotice("已恢复默认布局");
  };

  return (
    <div className="page layout-page">
      <div className="page-toolbar layout-toolbar">
        <div className="breadcrumb"><button type="button" aria-label="返回插件市场" onClick={onBack}>‹</button><span>插件市场</span><span>›</span><strong>界面与插件布局</strong></div>
        <div className="layout-toolbar-actions"><button type="button" onClick={restore}><RotateCcw />恢复默认</button></div>
      </div>

      <div className="layout-intro"><h2>界面与插件布局</h2><p>调整插件顺序和可见位置。核心入口始终保留，方便恢复。</p></div>

      <div className="layout-body">
        <section className="layout-preview-column">
          <h3>当前设备侧边栏</h3>
          <div className="rail-preview">
            <h4>{displayName}</h4><p>{productCode}</p><small>● {snapshot.connection === "ready" ? "真机已连接" : "尚未连接设备"}</small>
            <div className="preview-divider" /><strong>核心（不可移除）</strong>
            <div className="preview-core"><Home />设备 <span>▣</span></div>
            <div className="preview-core"><span aria-hidden="true">✚</span><span>插件</span><span>▣</span></div>
            <div className="preview-divider" /><strong>已启用插件</strong>
            {enabled.map((plugin) => (
              <div key={plugin.manifest.id} className={plugin.manifest.id === ai.manifest.id ? "preview-plugin selected" : "preview-plugin"}>
                <GripVertical /><span>{plugin.manifest.name}</span><span className={sidebarIds.includes(plugin.manifest.id) ? "mini-switch on" : "mini-switch"} />
              </div>
            ))}
          </div>
        </section>

        <section className="placement-editor">
          <div className="layout-selectors">
            <label>布局方案<select aria-label="布局方案" value={activeId} onChange={(event) => setActiveId(event.target.value)}>{profileOptions.map((profile) => <option key={profile.id} value={profile.id}>{profile.name}</option>)}</select></label>
            <span>当前设备：{displayName}</span>
          </div>
          <h3>AI 节点可以出现的位置</h3>
          {placementOptions.map(({ slot, label, detail, icon: Icon }) => {
            const checked = active.placements[slot].some((entry) => entry.pluginId === ai.manifest.id && entry.visible);
            return (
              <div className="placement-row" key={slot}>
                <Icon />
                <div><strong>{label}</strong><span>{detail}</span></div>
                <label className="switch-label"><span className="sr-only">AI 节点：{label}</span><input type="checkbox" role="switch" aria-label={`AI 节点：${label}`} checked={checked} onChange={(event) => toggle(slot, event.target.checked)} /><span className="switch-track" /></label>
                <select aria-label={`${label}顺序`} value={(active.placements[slot].findIndex((entry) => entry.pluginId === ai.manifest.id) + 1).toString()} onChange={(event) => {
                  const updated = movePlacement(active, slot, ai.manifest.id, Number(event.target.value) - 1);
                  setDraftProfiles((current) => current.map((profile) => profile.id === active.id ? updated : profile));
                  setDirty((count) => count + 1);
                }}><option>1</option><option>2</option><option>3</option></select>
              </div>
            );
          })}
          <p className="permission-note">布局编辑不会增加插件权限</p>
        </section>

      </div>

      <div className="layout-savebar"><span>{notice || (dirty > 0 ? `未保存的更改 ${dirty} 项` : "布局与已保存版本一致")}</span><button type="button" onClick={onBack}>取消</button><button className="primary-button" type="button" onClick={() => { onProfilesChange(draftProfiles); setDirty(0); setNotice("布局已保存到本机"); }}><Save />保存布局</button></div>
    </div>
  );
}

export function createAcceptedLayoutProfiles(): LayoutProfile[] {
  const ids = [
    "dev.opendevice.device-inspection",
    "dev.opendevice.ai-readiness",
    "dev.opendevice.remote-gateway",
  ];
  const basePlacements = {
    sidebar: ids.map((pluginId) => ({ pluginId, visible: true })),
    overview: ids.map((pluginId) => ({ pluginId, visible: pluginId !== "dev.opendevice.remote-gateway" })),
    shortcut: ids.map((pluginId) => ({ pluginId, visible: true })),
    service: ids.map((pluginId) => ({ pluginId, visible: pluginId === "dev.opendevice.remote-gateway" })),
    report: ids.map((pluginId) => ({ pluginId, visible: pluginId !== "dev.opendevice.remote-gateway" })),
    context: ids.map((pluginId) => ({ pluginId, visible: false })),
  };
  const clone = () => Object.fromEntries(Object.entries(basePlacements).map(([slot, entries]) => [slot, entries.map((entry) => ({ ...entry }))])) as LayoutProfile["placements"];
  const daily = clone();
  const aiOverview = daily.overview.find((entry) => entry.pluginId === "dev.opendevice.ai-readiness");
  if (aiOverview) aiOverview.visible = false;
  return [
    { id: "default", name: "默认布局", scope: { kind: "all" }, placements: clone() },
    { id: "daily", name: "日常使用", scope: { kind: "all" }, placements: daily },
    { id: "current", name: "仅当前设备", scope: { kind: "current" }, placements: clone() },
  ];
}
