import React, { useState, useEffect, useRef } from 'react';
import { message } from 'antd';
import { specApi, PrototypeSpec } from './api';
import { SafeMarkdown } from '@/lib/markdown';

interface PrototypeSpecEditorProps {
  prototypeId: string;
  readOnly?: boolean;
}

export const PrototypeSpecEditor: React.FC<PrototypeSpecEditorProps> = ({
  prototypeId,
  readOnly = false,
}) => {
  const [spec, setSpec] = useState<PrototypeSpec>({
    prototypePublicId: prototypeId,
    goal: '',
    coreFlow: '',
    interactionRules: '',
    businessConstraints: '',
    dataRequirements: '',
    acceptanceNotes: '',
    markdownExtra: '',
    rowVersion: 0,
    updatedAt: '',
  });

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savedTime, setSavedTime] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [activeTab, setActiveTab] = useState<'edit' | 'preview'>('edit');

  const debounceTimerRef = useRef<NodeJS.Timeout | null>(null);
  const isDirtyRef = useRef(false);

  const loadSpec = async () => {
    try {
      setLoading(true);
      setError(null);
      setConflict(false);
      const data = await specApi.get(prototypeId);
      setSpec(data);
      isDirtyRef.current = false;
    } catch (err: any) {
      setError(err?.message || '获取说明失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSpec();
  }, [prototypeId]);

  const saveSpec = async (currentSpec: PrototypeSpec) => {
    if (readOnly) return;
    try {
      setSaving(true);
      setError(null);
      const updated = await specApi.update(prototypeId, {
        goal: currentSpec.goal,
        coreFlow: currentSpec.coreFlow,
        interactionRules: currentSpec.interactionRules,
        businessConstraints: currentSpec.businessConstraints,
        dataRequirements: currentSpec.dataRequirements,
        acceptanceNotes: currentSpec.acceptanceNotes,
        markdownExtra: currentSpec.markdownExtra,
        rowVersion: currentSpec.rowVersion,
      });
      setSpec(updated);
      setSavedTime(new Date().toLocaleTimeString());
      setConflict(false);
      isDirtyRef.current = false;
    } catch (err: any) {
      if (err?.code === 'RESOURCE_VERSION_CONFLICT') {
        setConflict(true);
      } else {
        setError(err?.message || '保存失败');
      }
    } finally {
      setSaving(false);
    }
  };

  const handleFieldChange = (field: keyof PrototypeSpec, value: string) => {
    if (readOnly) return;
    const updated = { ...spec, [field]: value };
    setSpec(updated);
    isDirtyRef.current = true;

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    debounceTimerRef.current = setTimeout(() => {
      saveSpec(updated);
    }, 800);
  };

  const copyLocalContent = () => {
    const text = `
# 功能目标
${spec.goal}

# 核心流程
${spec.coreFlow}

# 交互规则
${spec.interactionRules}

# 业务约束
${spec.businessConstraints}

# 数据要求
${spec.dataRequirements}

# 验收说明
${spec.acceptanceNotes}

# 补充说明
${spec.markdownExtra}
    `.trim();

    navigator.clipboard.writeText(text);
    message.success('已复制本地草稿到剪贴板！');
  };

  if (loading) {
    return <div className="py-8 text-center text-sm text-mute">加载说明中...</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header bar */}
      <div className="flex items-center justify-between border-b border-signal/15 pb-3">
        <div>
          <h3 className="text-lg font-semibold text-ink">原型结构化说明与约束</h3>
          <p className="text-xs text-mute">
            {readOnly
              ? '只读模式'
              : saving
              ? '正在自动保存...'
              : savedTime
              ? `已自动保存 (${savedTime})`
              : '输入时将以 800ms 防抖自动保存'}
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div className="flex rounded-lg border border-signal/20 bg-void/70 p-1 text-xs">
            <button
              onClick={() => setActiveTab('edit')}
              className={`rounded px-3 py-1 font-medium transition ${activeTab === 'edit' ? 'bg-signal/20 text-signal border border-signal/30 shadow-sm' : 'text-mute hover:text-ink'}`}
            >
              编辑模式
            </button>
            <button
              onClick={() => setActiveTab('preview')}
              className={`rounded px-3 py-1 font-medium transition ${activeTab === 'preview' ? 'bg-signal/20 text-signal border border-signal/30 shadow-sm' : 'text-mute hover:text-ink'}`}
            >
              预览模式
            </button>
          </div>
        </div>
      </div>

      {/* Conflict banner */}
      {conflict && (
        <div className="rounded-lg border border-amber-400/30 bg-amber-500/10 p-4 text-sm text-amber-200 shadow-sm">
          <div className="font-semibold text-amber-300">⚠️ 检测到版本冲突</div>
          <p className="mt-1 text-amber-200/90">其他用户已保存了新版本的说明内容。系统已为您保留当前输入的草稿，避免覆盖他人改动。</p>
          <div className="mt-3 flex gap-3">
            <button
              onClick={copyLocalContent}
              className="rounded-md border border-amber-400/40 bg-amber-400/10 px-3 py-1.5 text-xs font-medium text-amber-200 shadow-sm hover:bg-amber-400/20"
            >
              复制本地草稿到剪贴板
            </button>
            <button
              onClick={loadSpec}
              className="rounded-md bg-amber-600/80 px-3 py-1.5 text-xs font-medium text-white shadow-sm hover:bg-amber-600"
            >
              放弃本地改动并重新加载
            </button>
          </div>
        </div>
      )}

      {error && (
        <div className="rounded-md border border-red-400/30 bg-red-500/10 p-3 text-sm text-red-300">
          {error}
        </div>
      )}

      {activeTab === 'preview' ? (
        <div className="space-y-6 rounded-xl border border-signal/15 bg-panel/70 p-6 backdrop-blur-md shadow-lg">
          <section>
            <h4 className="text-base font-bold text-ink">一、功能目标</h4>
            <p className="mt-1.5 whitespace-pre-wrap text-sm text-mute leading-relaxed">{spec.goal || '未填写'}</p>
          </section>
          <section>
            <h4 className="text-base font-bold text-ink">二、核心流程</h4>
            <p className="mt-1.5 whitespace-pre-wrap text-sm text-mute leading-relaxed">{spec.coreFlow || '未填写'}</p>
          </section>
          <section>
            <h4 className="text-base font-bold text-ink">三、交互规则</h4>
            <p className="mt-1.5 whitespace-pre-wrap text-sm text-mute leading-relaxed">{spec.interactionRules || '未填写'}</p>
          </section>
          <section>
            <h4 className="text-base font-bold text-ink">四、业务约束</h4>
            <p className="mt-1.5 whitespace-pre-wrap text-sm text-mute leading-relaxed">{spec.businessConstraints || '未填写'}</p>
          </section>
          <section>
            <h4 className="text-base font-bold text-ink">五、数据要求</h4>
            <p className="mt-1.5 whitespace-pre-wrap text-sm text-mute leading-relaxed">{spec.dataRequirements || '未填写'}</p>
          </section>
          <section>
            <h4 className="text-base font-bold text-ink">六、验收说明</h4>
            <p className="mt-1.5 whitespace-pre-wrap text-sm text-mute leading-relaxed">{spec.acceptanceNotes || '未填写'}</p>
          </section>
          <section>
            <h4 className="text-base font-bold text-ink">七、补充 Markdown 说明</h4>
            <div className="mt-2 rounded-lg border border-signal/10 bg-void/70 p-4">
              <SafeMarkdown source={spec.markdownExtra || '无补充说明'} />
            </div>
          </section>
        </div>
      ) : (
        <div className="space-y-5">
          <div>
            <label className="block text-sm font-semibold text-ink">一、功能目标</label>
            <textarea
              rows={3}
              disabled={readOnly}
              value={spec.goal}
              onChange={(e) => handleFieldChange('goal', e.target.value)}
              placeholder="明确该原型旨在解决的业务问题和期望达成的主要目标..."
              className="mt-1.5 block w-full rounded-lg border border-signal/20 bg-void/80 p-3 text-sm text-ink placeholder:text-mute/50 focus:border-signal focus:outline-none focus:ring-1 focus:ring-signal/40 focus:shadow-signal-focus disabled:bg-void/40 disabled:text-mute/50 disabled:border-signal/10 transition"
            />
          </div>

          <div>
            <label className="block text-sm font-semibold text-ink">二、核心流程</label>
            <textarea
              rows={3}
              disabled={readOnly}
              value={spec.coreFlow}
              onChange={(e) => handleFieldChange('coreFlow', e.target.value)}
              placeholder="主路径操作步骤与分支场景..."
              className="mt-1.5 block w-full rounded-lg border border-signal/20 bg-void/80 p-3 text-sm text-ink placeholder:text-mute/50 focus:border-signal focus:outline-none focus:ring-1 focus:ring-signal/40 focus:shadow-signal-focus disabled:bg-void/40 disabled:text-mute/50 disabled:border-signal/10 transition"
            />
          </div>

          <div>
            <label className="block text-sm font-semibold text-ink">三、交互规则</label>
            <textarea
              rows={3}
              disabled={readOnly}
              value={spec.interactionRules}
              onChange={(e) => handleFieldChange('interactionRules', e.target.value)}
              placeholder="表单交互、校验逻辑、跳转行为与动效约定..."
              className="mt-1.5 block w-full rounded-lg border border-signal/20 bg-void/80 p-3 text-sm text-ink placeholder:text-mute/50 focus:border-signal focus:outline-none focus:ring-1 focus:ring-signal/40 focus:shadow-signal-focus disabled:bg-void/40 disabled:text-mute/50 disabled:border-signal/10 transition"
            />
          </div>

          <div>
            <label className="block text-sm font-semibold text-ink">四、业务约束</label>
            <textarea
              rows={3}
              disabled={readOnly}
              value={spec.businessConstraints}
              onChange={(e) => handleFieldChange('businessConstraints', e.target.value)}
              placeholder="权限管控、业务规则边界与外部依赖条件..."
              className="mt-1.5 block w-full rounded-lg border border-signal/20 bg-void/80 p-3 text-sm text-ink placeholder:text-mute/50 focus:border-signal focus:outline-none focus:ring-1 focus:ring-signal/40 focus:shadow-signal-focus disabled:bg-void/40 disabled:text-mute/50 disabled:border-signal/10 transition"
            />
          </div>

          <div>
            <label className="block text-sm font-semibold text-ink">五、数据要求</label>
            <textarea
              rows={3}
              disabled={readOnly}
              value={spec.dataRequirements}
              onChange={(e) => handleFieldChange('dataRequirements', e.target.value)}
              placeholder="核心字段枚举、类型、必填项与持久化规则..."
              className="mt-1.5 block w-full rounded-lg border border-signal/20 bg-void/80 p-3 text-sm text-ink placeholder:text-mute/50 focus:border-signal focus:outline-none focus:ring-1 focus:ring-signal/40 focus:shadow-signal-focus disabled:bg-void/40 disabled:text-mute/50 disabled:border-signal/10 transition"
            />
          </div>

          <div>
            <label className="block text-sm font-semibold text-ink">六、验收说明</label>
            <textarea
              rows={3}
              disabled={readOnly}
              value={spec.acceptanceNotes}
              onChange={(e) => handleFieldChange('acceptanceNotes', e.target.value)}
              placeholder="关键验收用例与交付标准..."
              className="mt-1.5 block w-full rounded-lg border border-signal/20 bg-void/80 p-3 text-sm text-ink placeholder:text-mute/50 focus:border-signal focus:outline-none focus:ring-1 focus:ring-signal/40 focus:shadow-signal-focus disabled:bg-void/40 disabled:text-mute/50 disabled:border-signal/10 transition"
            />
          </div>

          <div>
            <label className="block text-sm font-semibold text-ink">七、补充 Markdown 说明</label>
            <textarea
              rows={5}
              disabled={readOnly}
              value={spec.markdownExtra}
              onChange={(e) => handleFieldChange('markdownExtra', e.target.value)}
              placeholder="支持标准 Markdown 语法，禁止嵌入原始脚本与外部弹窗..."
              className="mt-1.5 block w-full rounded-lg border border-signal/20 bg-void/80 p-3 text-sm text-ink placeholder:text-mute/50 focus:border-signal focus:outline-none focus:ring-1 focus:ring-signal/40 focus:shadow-signal-focus disabled:bg-void/40 disabled:text-mute/50 disabled:border-signal/10 font-mono transition"
            />
          </div>
        </div>
      )}
    </div>
  );
};
