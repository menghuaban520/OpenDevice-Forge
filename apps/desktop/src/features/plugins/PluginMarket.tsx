import { useMemo, useState } from "react";
import {
  BrainCircuit,
  ChevronRight,
  Cloud,
  FileOutput,
  HelpCircle,
  RefreshCw,
  Search,
  Shield,
  Stethoscope,
} from "lucide-react";
import type {
  PluginManifest,
  PluginRegistryAction,
  PluginRegistryState,
} from "@opendevice/core";
import { PluginDetail } from "./PluginDetail";

interface PluginMarketProps {
  catalog: PluginManifest[];
  registry: PluginRegistryState;
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

const listSource = (manifest: PluginManifest) =>
  manifest.source.kind === "official" ? "官方" : manifest.source.kind === "github" ? "GitHub" : manifest.source.kind === "community" ? "社区" : "本地";

export function PluginMarket({ catalog, registry, onAction, onEditLayout }: PluginMarketProps) {
  const visibleCatalog = catalog.filter((manifest) => !manifest.protected);
  const [selectedId, setSelectedId] = useState(
    visibleCatalog.find((manifest) => manifest.id.includes("ai-readiness"))?.id ?? visibleCatalog[0]?.id ?? "",
  );
  const [query, setQuery] = useState("");
  const [tab, setTab] = useState<"installed" | "discover" | "updates" | "sources">("installed");
  const selected = visibleCatalog.find((manifest) => manifest.id === selectedId) ?? visibleCatalog[0];
  const filtered = useMemo(
    () => visibleCatalog.filter((manifest) => {
      const matchesQuery = `${manifest.name}${manifest.summary}${manifest.publisher}`.toLowerCase().includes(query.toLowerCase());
      if (!matchesQuery) return false;
      if (tab === "installed") return Boolean(registry.plugins[manifest.id]);
      if (tab === "discover") return !registry.plugins[manifest.id];
      if (tab === "updates") return false;
      return true;
    }),
    [query, registry.plugins, tab, visibleCatalog],
  );

  if (!selected) return null;

  return (
    <div className="page plugin-market-page">
      <div className="page-toolbar market-toolbar">
        <div className="breadcrumb"><button type="button" aria-label="后退">‹</button><button type="button" aria-label="前进">›</button><span>插件市场</span><ChevronRight size={14} /><strong>{selected.name}</strong></div>
        <label className="search-box wide"><Search size={17} /><span className="sr-only">搜索插件</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索插件或 GitHub 仓库" /></label>
        <button type="button" className="icon-button" aria-label="刷新目录"><RefreshCw /></button>
        <button type="button" className="icon-button" aria-label="插件帮助"><HelpCircle /></button>
      </div>

      <section className="catalog-pane">
        <div className="catalog-tabs" role="tablist">
          {[
            ["installed", "已安装"],
            ["discover", "发现"],
            ["updates", "更新"],
            ["sources", "来源"],
          ].map(([id, label]) => <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? "active" : ""} type="button" onClick={() => setTab(id as typeof tab)}>{label}</button>)}
        </div>
        <div className="catalog-list">
          {filtered.map((manifest) => {
            const Icon = iconFor(manifest.id);
            const installed = registry.plugins[manifest.id];
            return (
              <button
                type="button"
                key={manifest.id}
                aria-label={`${manifest.name} 插件`}
                className={selected.id === manifest.id ? "plugin-list-item selected" : "plugin-list-item"}
                onClick={() => setSelectedId(manifest.id)}
              >
                <Icon />
                <span className="plugin-list-copy"><strong>{manifest.name}</strong><span>{manifest.summary}</span><small>v{manifest.version} · {listSource(manifest)}</small></span>
                <span className={installed?.enabled ? "list-state enabled" : "list-state"}>{installed ? (installed.enabled ? "启用" : "停用") : "未安装"}</span>
              </button>
            );
          })}
          {filtered.length === 0 ? <div className="catalog-empty">{tab === "updates" ? "当前没有可用更新" : "这个分类里暂时没有插件"}</div> : null}
        </div>
      </section>

      <div className="market-detail-wrap">
        <PluginDetail
          manifest={selected}
          {...(registry.plugins[selected.id] ? { installed: registry.plugins[selected.id] } : {})}
          onAction={onAction}
          onEditLayout={onEditLayout}
        />
      </div>
    </div>
  );
}
