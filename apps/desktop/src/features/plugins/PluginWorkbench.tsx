import { useEffect, useRef, useState } from "react";
import {
  AlertCircle,
  CheckCircle2,
  ChevronLeft,
  LoaderCircle,
  Play,
  RotateCcw,
} from "lucide-react";
import type {
  PluginConfiguration,
  PluginConfigurationField,
  PluginManifest,
  PluginRunProgress,
  PluginRunResult,
  PluginRunState,
  WorkflowRuntimeDefinition,
} from "@opendevice/core";

type WorkbenchTab = "run" | "configuration" | "result";

interface PluginWorkbenchProps<TContext> {
  manifest: PluginManifest;
  runtime: WorkflowRuntimeDefinition<TContext>;
  context: TContext;
  configuration: PluginConfiguration;
  onConfigurationChange(configuration: PluginConfiguration): void;
  canRun: boolean;
  unavailableReason?: string;
  onBack?: () => void;
}

interface ConfigurationFieldProps {
  field: PluginConfigurationField;
  configuration: PluginConfiguration;
  error?: string;
  onChange(configuration: PluginConfiguration): void;
}

const ConfigurationField = ({
  field,
  configuration,
  error,
  onChange,
}: ConfigurationFieldProps) => {
  const update = (value: PluginConfiguration[string]) => {
    onChange({ ...configuration, [field.id]: value });
  };
  const value = configuration[field.id];

  if (field.type === "checkbox-group") {
    const selected = Array.isArray(value) ? value : [];
    return (
      <fieldset className="workbench-field workbench-checkbox-group">
        <legend>{field.label}</legend>
        {field.description ? <p>{field.description}</p> : null}
        <div>
          {field.options.map((option) => (
            <label key={option.value}>
              <input
                type="checkbox"
                checked={selected.includes(option.value)}
                onChange={(event) => update(event.target.checked
                  ? [...selected, option.value]
                  : selected.filter((item) => item !== option.value))}
              />
              <span>
                <strong>{option.label}</strong>
                {option.description ? <small>{option.description}</small> : null}
              </span>
            </label>
          ))}
        </div>
        {error ? <span className="field-error">{error}</span> : null}
      </fieldset>
    );
  }

  if (field.type === "toggle") {
    return (
      <label className="workbench-field workbench-toggle">
        <span>
          <strong>{field.label}</strong>
          {field.description ? <small>{field.description}</small> : null}
        </span>
        <input
          type="checkbox"
          role="switch"
          aria-label={field.label}
          checked={value === true}
          onChange={(event) => update(event.target.checked)}
        />
        {error ? <span className="field-error">{error}</span> : null}
      </label>
    );
  }

  if (field.type === "number") {
    return (
      <label className="workbench-field workbench-input-field">
        <span>{field.label}</span>
        <input
          type="number"
          value={typeof value === "number" ? value : ""}
          min={field.min}
          max={field.max}
          step={field.step}
          onChange={(event) => update(Number(event.target.value))}
        />
        {field.description ? <small>{field.description}</small> : null}
        {error ? <span className="field-error">{error}</span> : null}
      </label>
    );
  }

  if (field.type === "text") {
    return (
      <label className="workbench-field workbench-input-field">
        <span>{field.label}</span>
        <input
          type="text"
          value={typeof value === "string" ? value : ""}
          placeholder={field.placeholder}
          onChange={(event) => update(event.target.value)}
        />
        {field.description ? <small>{field.description}</small> : null}
        {error ? <span className="field-error">{error}</span> : null}
      </label>
    );
  }

  return (
    <label className="workbench-field workbench-input-field">
      <span>{field.label}</span>
      <select
        value={typeof value === "string" ? value : ""}
        onChange={(event) => update(event.target.value)}
      >
        {field.options.map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>
      {field.description ? <small>{field.description}</small> : null}
      {error ? <span className="field-error">{error}</span> : null}
    </label>
  );
};

const statusLabel: Record<PluginRunState, string> = {
  idle: "尚未运行",
  running: "正在运行",
  success: "运行完成",
  error: "运行失败",
  cancelled: "已取消",
};

export function PluginWorkbench<TContext>({
  manifest,
  runtime,
  context,
  configuration,
  onConfigurationChange,
  canRun,
  unavailableReason,
  onBack,
}: PluginWorkbenchProps<TContext>) {
  const [tab, setTab] = useState<WorkbenchTab>("run");
  const [runState, setRunState] = useState<PluginRunState>("idle");
  const [progress, setProgress] = useState<PluginRunProgress | null>(null);
  const [result, setResult] = useState<PluginRunResult | null>(null);
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => () => controllerRef.current?.abort(), []);

  const updateConfiguration = (next: PluginConfiguration) => {
    setValidationErrors(runtime.validate(next));
    onConfigurationChange(next);
  };

  const run = async () => {
    if (runState === "running" || !canRun) return;
    const errors = runtime.validate(configuration);
    setValidationErrors(errors);
    if (Object.keys(errors).length > 0) {
      setTab("configuration");
      return;
    }

    const controller = new AbortController();
    controllerRef.current = controller;
    setRunState("running");
    setProgress({ phase: "starting", message: "正在准备插件…" });
    try {
      const nextResult = await runtime.execute({
        context,
        configuration,
        signal: controller.signal,
        onProgress: setProgress,
      });
      if (controller.signal.aborted) {
        setRunState("cancelled");
        return;
      }
      setResult(nextResult);
      setRunState("success");
      setTab("result");
    } catch {
      if (controller.signal.aborted) {
        setRunState("cancelled");
      } else {
        setRunState("error");
        setProgress(null);
        setTab("run");
      }
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null;
    }
  };

  const hasConfiguration = Boolean(runtime.configuration?.fields.length);
  const tabs: Array<{ id: WorkbenchTab; label: string }> = [
    { id: "run", label: "运行" },
    ...(hasConfiguration ? [{ id: "configuration" as const, label: "配置" }] : []),
    { id: "result", label: "结果" },
  ];

  return (
    <div className="page plugin-workbench-page">
      <header className="workbench-header">
        {onBack ? (
          <button type="button" className="workbench-back" onClick={onBack}>
            <ChevronLeft />返回插件市场
          </button>
        ) : null}
        <div>
          <span className="workbench-kicker">插件工作台</span>
          <h1>{manifest.name}</h1>
          <p>{manifest.summary}</p>
        </div>
        <span className={`workbench-state state-${runState}`}>
          {runState === "running" ? <LoaderCircle className="spin" /> : null}
          {runState === "success" ? <CheckCircle2 /> : null}
          {runState === "error" ? <AlertCircle /> : null}
          {statusLabel[runState]}
        </span>
      </header>

      <div className="workbench-tabs" role="tablist" aria-label="插件工作台">
        {tabs.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={tab === item.id}
            className={tab === item.id ? "active" : ""}
            onClick={() => setTab(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <main className="workbench-content">
        {tab === "run" ? (
          <section className="workbench-run" role="tabpanel">
            <div className="workbench-run-card">
              <div className="run-card-copy">
                <span>当前任务</span>
                <h2>{manifest.name}</h2>
                <p>插件只会执行配置中已启用的项目，结果保留在本机工作台。</p>
              </div>
              <div className="run-card-action">
                <button
                  type="button"
                  className="primary-button run-plugin-button"
                  disabled={!canRun || runState === "running"}
                  onClick={() => void run()}
                >
                  {runState === "running" ? <LoaderCircle className="spin" /> : <Play />}
                  {runState === "running" ? "正在运行…" : `运行${manifest.name}`}
                </button>
                {!canRun && unavailableReason ? <p>{unavailableReason}</p> : null}
              </div>
            </div>

            {runState === "running" && progress ? (
              <div className="workbench-notice running" aria-live="polite">
                <LoaderCircle className="spin" />
                <div><strong>插件正在工作</strong><span>{progress.message}</span></div>
              </div>
            ) : null}
            {runState === "error" ? (
              <div className="workbench-notice error" role="alert">
                <AlertCircle />
                <div><strong>运行失败，请检查连接后重试</strong><span>没有修改手机，也没有保留未完成结果。</span></div>
                <button type="button" onClick={() => void run()}><RotateCcw />重试</button>
              </div>
            ) : null}
          </section>
        ) : null}

        {tab === "configuration" && runtime.configuration ? (
          <section className="workbench-configuration" role="tabpanel">
            <div className="workbench-section-heading">
              <div><h2>运行配置</h2><p>只选择这次真正需要执行的项目。</p></div>
              <button type="button" onClick={() => updateConfiguration(runtime.defaultConfiguration)}>恢复默认</button>
            </div>
            <div className="workbench-fields">
              {runtime.configuration.fields.map((field) => (
                <ConfigurationField
                  key={field.id}
                  field={field}
                  configuration={configuration}
                  {...(validationErrors[field.id]
                    ? { error: validationErrors[field.id] }
                    : {})}
                  onChange={updateConfiguration}
                />
              ))}
            </div>
          </section>
        ) : null}

        {tab === "result" ? (
          <section className="workbench-results" role="tabpanel">
            {result ? (
              <>
                <div className="workbench-section-heading">
                  <div><h2>本次结果</h2><p>仅显示本次配置实际执行的项目。</p></div>
                  <button type="button" onClick={() => setTab("run")}>再次运行</button>
                </div>
                <div className="result-groups">
                  {result.groups.map((group) => (
                    <article key={group.id} className="result-group">
                      <h3>{group.label}</h3>
                      <dl>
                        {group.items.map((item) => (
                          <div key={item.id} className={`result-${item.status}`}>
                            <dt>{item.label}</dt><dd>{item.value}</dd>
                          </div>
                        ))}
                      </dl>
                    </article>
                  ))}
                </div>
              </>
            ) : (
              <div className="workbench-empty-result">
                <CheckCircle2 />
                <h2>还没有运行结果</h2>
                <p>回到“运行”开始第一次任务。</p>
                <button type="button" onClick={() => setTab("run")}>去运行</button>
              </div>
            )}
          </section>
        ) : null}
      </main>
    </div>
  );
}
