import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { message } from 'antd';
import { sharePublicApi, BootstrapResponse } from './api';
import { PrototypePreview } from '@/features/versions/PrototypePreview';
import { SafeMarkdown } from '@/lib/markdown';
import { CommentThread } from '@/features/comments/CommentThread';
import { StageBackdrop } from '@/components/visual/StageBackdrop';
import { GlassPanel } from '@/components/visual/GlassPanel';
import { BrandMark } from '@/components/visual/BrandMark';

export const SharedPrototypePage: React.FC = () => {
  const { token } = useParams<{ token: string }>();

  const [loading, setLoading] = useState(true);
  const [bootstrap, setBootstrap] = useState<BootstrapResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Password submission state
  const [password, setPassword] = useState('');
  const [guestName, setGuestName] = useState('');
  const [verifying, setVerifying] = useState(false);
  const [verifyError, setVerifyError] = useState<string | null>(null);

  // Content ticket state
  const [contentUrl, setContentUrl] = useState<string | null>(null);
  const [loadingContent, setLoadingContent] = useState(false);

  const initShare = async () => {
    if (!token) return;
    try {
      setLoading(true);
      setError(null);
      const data = await sharePublicApi.bootstrap(token);
      setBootstrap(data);

      if (data.authenticated && !data.requiresPassword) {
        await loadContentTicket(token, data.csrfToken);
      }
    } catch (e: any) {
      setError(e?.message || '加载分享失败');
    } finally {
      setLoading(false);
    }
  };

  const loadContentTicket = async (rawToken: string, csrf?: string) => {
    try {
      setLoadingContent(true);
      const res = await sharePublicApi.issueContentTicket(rawToken, csrf);
      setContentUrl(res.contentUrl);
    } catch (e: any) {
      setError(e?.message || '获取原型内容凭据失败');
    } finally {
      setLoadingContent(false);
    }
  };

  useEffect(() => {
    initShare();
  }, [token]);

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !password) return;

    try {
      setVerifying(true);
      setVerifyError(null);
      const res = await sharePublicApi.verifyPassword(token, password, guestName);
      if (res.success) {
        // Re-bootstrap or load ticket
        const data = await sharePublicApi.bootstrap(token);
        setBootstrap(data);
        await loadContentTicket(token, res.csrfToken);
      }
    } catch (err: any) {
      setVerifyError(err?.response?.data?.message || err?.message || '访问密码错误');
    } finally {
      setVerifying(false);
    }
  };

  if (loading) {
    return (
      <StageBackdrop density="app">
        <div className="flex min-h-screen items-center justify-center font-mono text-xs tracking-[0.28em] text-mute">
          正在安全加载原型分享...
        </div>
      </StageBackdrop>
    );
  }

  if (error || (bootstrap && bootstrap.statusMessage !== 'OK')) {
    return (
      <StageBackdrop density="app">
        <div className="grid min-h-screen place-items-center p-4">
          <GlassPanel className="w-full max-w-md text-center">
            <div className="font-display text-3xl text-ember">OFFLINE</div>
            <h2 className="mt-4 font-display text-lg tracking-wide text-ink">
              {bootstrap?.statusMessage || '原型分享不可用'}
            </h2>
            <p className="mt-2 text-xs text-mute">
              {error || '该分享可能已被停用、链接已过期，或对应原型已被归档/删除。'}
            </p>
          </GlassPanel>
        </div>
      </StageBackdrop>
    );
  }

  if (bootstrap?.requiresPassword) {
    return (
      <StageBackdrop density="login">
        <div className="grid min-h-screen place-items-center p-4">
          <GlassPanel className="w-full max-w-sm">
            <div className="text-center">
              <BrandMark className="mx-auto mb-4 h-10 w-10" />
              <h2 className="font-display text-lg tracking-wide text-ink">受密码保护的分享</h2>
              <p className="mt-1 text-xs text-mute">
                请输入发起人提供的访问密码以预览「{bootstrap.prototypeName}」
              </p>
            </div>

            <form onSubmit={handlePasswordSubmit} className="mt-6 space-y-4">
              {verifyError ? (
                <div className="rounded-md border border-red-400/30 bg-red-500/10 p-2.5 text-xs text-red-300">
                  {verifyError}
                </div>
              ) : null}

              <div>
                <label className="block text-xs font-semibold text-mute">访问密码 *</label>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="请输入访问密码"
                  className="mt-1 block w-full rounded-md border border-signal/20 bg-void px-3 py-2 text-xs text-ink outline-none transition focus:border-signal focus:shadow-signal-focus"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-mute">您的昵称/称呼 (选填)</label>
                <input
                  type="text"
                  value={guestName}
                  onChange={(e) => setGuestName(e.target.value)}
                  placeholder="例如：评审人-张老师"
                  className="mt-1 block w-full rounded-md border border-signal/20 bg-void px-3 py-2 text-xs text-ink outline-none transition focus:border-signal focus:shadow-signal-focus"
                />
              </div>

              <button
                type="submit"
                disabled={verifying}
                className="w-full rounded-md bg-signal py-2 text-xs font-semibold text-void shadow-glow transition hover:brightness-110 disabled:opacity-50"
              >
                {verifying ? '验证中...' : '进入预览'}
              </button>
            </form>
          </GlassPanel>
        </div>
      </StageBackdrop>
    );
  }

  return (
    <StageBackdrop density="app">
      <div className="flex min-h-screen flex-col">
        <header className="flex h-14 items-center justify-between border-b border-signal/10 bg-void/70 px-6 backdrop-blur-xl">
          <div className="flex items-center gap-3">
            <BrandMark className="h-8 w-8" />
            <span className="font-display tracking-wide text-ink">{bootstrap?.prototypeName}</span>
            <span className="rounded border border-signal/20 bg-signal/10 px-2 py-0.5 font-mono text-[10px] tracking-widest text-signal">
              对外分享预览
            </span>
          </div>
        </header>

        <main className="flex-1 space-y-6 p-6">
          {loadingContent ? (
            <div className="flex h-[750px] items-center justify-center rounded-xl border border-signal/10 bg-panel/70 text-sm text-mute backdrop-blur-md">
              正在建立沙箱凭据与内容通道...
            </div>
          ) : contentUrl ? (
            <PrototypePreview
              contentUrl={contentUrl}
              onRefreshTicket={() => token && loadContentTicket(token, bootstrap?.csrfToken)}
            />
          ) : (
            <div className="flex h-[750px] items-center justify-center rounded-xl border border-signal/10 bg-panel/70 text-sm text-mute backdrop-blur-md">
              无法获取内容通道
            </div>
          )}

          {bootstrap?.spec && Object.keys(bootstrap.spec).length > 0 ? (
            <div className="space-y-4 rounded-xl border border-signal/15 bg-panel/75 p-6 shadow-glow backdrop-blur-md">
              <h3 className="font-display text-base tracking-wide text-ink">产品设计说明与约束</h3>
              {bootstrap.spec.goal ? (
                <div>
                  <h4 className="text-xs font-semibold text-signal">一、功能目标</h4>
                  <p className="mt-1 text-xs text-mute">{bootstrap.spec.goal}</p>
                </div>
              ) : null}
              {bootstrap.spec.coreFlow ? (
                <div>
                  <h4 className="text-xs font-semibold text-signal">二、核心流程</h4>
                  <p className="mt-1 text-xs text-mute">{bootstrap.spec.coreFlow}</p>
                </div>
              ) : null}
              {bootstrap.spec.markdownExtra ? (
                <div>
                  <h4 className="text-xs font-semibold text-signal">三、补充说明</h4>
                  <div className="mt-1 rounded bg-void/60 p-3 text-xs text-ink">
                    <SafeMarkdown source={bootstrap.spec.markdownExtra} />
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}

          {bootstrap?.attachments && bootstrap.attachments.length > 0 ? (
            <div className="space-y-4 rounded-xl border border-signal/15 bg-panel/75 p-6 shadow-glow backdrop-blur-md">
              <h3 className="font-display text-base tracking-wide text-ink">公开附件与参考素材</h3>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                {bootstrap.attachments.map((att) => (
                  <div
                    key={att.publicId}
                    className="flex items-center justify-between gap-2 rounded-lg border border-signal/10 bg-void/50 p-3"
                  >
                    <div className="truncate text-xs font-medium text-ink">{att.name}</div>
                    <div className="flex shrink-0 items-center gap-2">
                      <span className="text-xs text-mute">{(att.size / 1024).toFixed(1)} KB</span>
                      {token ? (
                        <button
                          type="button"
                          className="text-xs font-medium text-signal hover:text-gold"
                          onClick={async () => {
                            try {
                              const ticket = await sharePublicApi.createAttachmentDownloadTicket(
                                token,
                                att.publicId,
                                bootstrap?.csrfToken
                              );
                              window.open(ticket.url, '_blank');
                            } catch (err: any) {
                              message.error(err?.message || '获取下载链接失败');
                            }
                          }}
                        >
                          下载
                        </button>
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          {bootstrap?.allowComment && token && bootstrap.currentVersionPublicId ? (
            <div className="pt-2">
              <CommentThread
                currentVersionId={bootstrap.currentVersionPublicId}
                currentVersionNo={bootstrap.currentVersionNo}
                isPublic={true}
                publicToken={token}
                csrfToken={bootstrap.csrfToken}
              />
            </div>
          ) : null}
        </main>
      </div>
    </StageBackdrop>
  );
};
