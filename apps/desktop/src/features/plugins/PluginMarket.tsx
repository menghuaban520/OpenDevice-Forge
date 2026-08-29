import { useMemo, useState } from "react";
import {
  BrainCircuit,
  Cloud,
  FileOutput,
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
  isRunnable: (manifest: PluginManifest) => boolean;
  onOpen: (manifest: PluginManifest) => void;
  onToggleSafeMode: () => void;
}

const iconFor = (id: string) => {
  if (id.includes("ai-readiness")) return BrainCircuit;
  if (id.includes("remote")) return Cloud;
  if (id.includes("report")) return FileOutput;
  if (id.includes("root")) return Shield;
  return Stethoscope;
};

const listSource = (manifest: PluginManifest) =>
  manifest.source.kind === "official" ? "内置" : manifest.source.kind === "github" ? "GitHub" : manifest.source.kind === "community" ? "社区" : "本地";

export function PluginMarket({
  catalog,
  registry,
  onAction,
  isRunnable,
  onOpen,
  onToggleSafeMode,
}: PluginMarketProps) {
  const visibleCatalog = catalog.filter((manifest) =>
    !manifest.protected && (isRunnable(manifest) || Boolean(registry.plugins[manifest.id]))
  );
  const [selectedId, setSelectedId] = useState(
    visibleCatalog.find((manifest) => manifest.id.includes("device-inspection"))?.id ?? visibleCatalog[0]?.id ?? "",
  );
  const [query, setQuery] = useState("");
  const [tab, setTab] = useState<"installed" | "discover">("discover");
  const selected = visibleCatalog.find((manifest) => manifest.id === selectedId) ?? visibleCatalog[0];
  const filtered = useMemo(
    () => visibleCatalog.filter((manifest) => {
      const matchesQuery = `${manifest.name}${manifest.summary}${manifest.publisher}`.toLowerCase().includes(query.toLowerCase());
      if (!matchesQuery) return false;
      if (tab === "installed") return Boolean(registry.plugins[manifest.id]);
      return true;
    }),
    [query, registry.plugins, tab, visibleCatalog],
  );

  if (!selected) return null;

  return (
    <div className="page plugin-market-page">
      <div className="page-toolbar market-toolbar">
        <div className="market-heading"><strong>模块中心</strong><span>当前目录来自应用内置清单</span></div>
        <label className="search-box wide"><Search size={17} /><span className="sr-only">搜索模块</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索内置模块" /></label>
      </div>

      <section className="catalog-pane">
        <div className="catalog-tabs" role="tablist">
          {[
            ["installed", "已安装"],
            ["discover", "内置模块"],
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
                aria-label={`${manifest.name} 模块`}
                className={selected.id === manifest.id ? "plugin-list-item selected" : "plugin-list-item"}
                onClick={() => setSelectedId(manifest.id)}
              >
                <Icon />
                <span className="plugin-list-copy"><strong>{manifest.name}</strong><span>{manifest.summary}</span><small>v{manifest.version} · {listSource(manifest)}</small></span>
                <span className={installed?.enabled ? "list-state enabled" : "list-state"}>{installed ? (installed.enabled ? "启用" : "停用") : "未安装"}</span>
              </button>
            );
          })}
          {filtered.length === 0 ? <div className="catalog-empty">这里没有匹配的内置模块。</div> : null}
        </div>
        <button type="button" className="market-safe-mode" onClick={onToggleSafeMode}>
          {registry.safeMode ? "退出故障恢复模式" : "进入故障恢复模式"}
        </button>
      </section>

      <div className="market-detail-wrap">
        <PluginDetail
          manifest={selected}
          {...(registry.plugins[selected.id] ? { installed: registry.plugins[selected.id] } : {})}
          runtimeAvailable={isRunnable(selected)}
          safeMode={registry.safeMode}
          onAction={onAction}
          onOpen={() => onOpen(selected)}
        />
      </div>
    </div>
  );
}
