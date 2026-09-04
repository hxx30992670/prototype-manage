import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { versionApi } from './api';
import { normalizeApiError } from '@/lib/http';

interface PrototypePreviewProps {
  prototypeId?: string;
  versionId?: string;
  title?: string;
  /** 受控模式：外部已持有内容 URL（如分享页），不再内部签发票据。 */
  contentUrl?: string;
  /** 受控模式下刷新票据的回调。 */
  onRefreshTicket?: () => void;
}

type DeviceMode = 'responsive' | 'desktop' | 'tablet' | 'mobile';

export const PrototypePreview: React.FC<PrototypePreviewProps> = ({
  prototypeId,
  versionId,
  title = '原型预览',
  contentUrl: controlledContentUrl,
  onRefreshTicket,
}) => {
  const isControlled = controlledContentUrl !== undefined;
  const [ticketInfo, setTicketInfo] = useState<{
    ticket: string;
    contentUrl: string;
    expiresAt: string;
  } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deviceMode, setDeviceMode] = useState<DeviceMode>('responsive');
  const [isFullScreen, setIsFullScreen] = useState(false);
  const [isPortalMode, setIsPortalMode] = useState(false);
  // 全屏时顶部工具栏是否以浮层形式展开（默认隐藏，鼠标移到顶部显示）
  const [toolbarVisible, setToolbarVisible] = useState(false);
  // 全屏时是否固定顶部工具栏：固定后常驻不随鼠标移出隐藏，取消后恢复 hover 逻辑
  const [toolbarPinned, setToolbarPinned] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const fetchTicket = async () => {
    if (!prototypeId) {
      setError('缺少原型 ID');
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const data = await versionApi.createPreviewTicket(prototypeId, versionId);
      setTicketInfo(data);
    } catch (err: unknown) {
      setError(normalizeApiError(err).message || '无法获取预览票据');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isControlled) {
      setLoading(false);
      return;
    }
    fetchTicket();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [prototypeId, versionId, isControlled]);

  const contentUrl = controlledContentUrl ?? ticketInfo?.contentUrl;
  const refresh = onRefreshTicket ?? fetchTicket;

  const getContainerWidth = () => {
    switch (deviceMode) {
      case 'mobile':
        return '375px';
      case 'tablet':
        return '768px';
      case 'desktop':
        return '1280px';
      default:
        return '100%';
    }
  };

  // 监听浏览器原生全屏状态变化
  useEffect(() => {
    const handleFullscreenChange = () => {
      const isNative = Boolean(
        document.fullscreenElement || (document as any).webkitFullscreenElement
      );
      if (!isNative) {
        if (!isPortalMode) {
          setIsFullScreen(false);
          setToolbarVisible(false);
          setToolbarPinned(false);
        }
      } else {
        setIsFullScreen(true);
        setIsPortalMode(false);
        setToolbarVisible(false);
        setToolbarPinned(false);
      }
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    document.addEventListener('webkitfullscreenchange', handleFullscreenChange);

    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
      document.removeEventListener('webkitfullscreenchange', handleFullscreenChange);
    };
  }, [isPortalMode]);

  // 处理 ESC 键退出全屏
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isFullScreen) {
        exitFullScreen();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isFullScreen, isPortalMode]);

  const enterFullScreen = async () => {
    setToolbarVisible(false);
    setToolbarPinned(false);
    const elem = containerRef.current;
    if (!elem) {
      setIsFullScreen(true);
      setIsPortalMode(true);
      return;
    }

    try {
      if (elem.requestFullscreen) {
        await elem.requestFullscreen();
        setIsFullScreen(true);
        setIsPortalMode(false);
      } else if ((elem as any).webkitRequestFullscreen) {
        await (elem as any).webkitRequestFullscreen();
        setIsFullScreen(true);
        setIsPortalMode(false);
      } else {
        setIsFullScreen(true);
        setIsPortalMode(true);
      }
    } catch {
      // 原生全屏受限或失败时，优雅降级为挂载到 body 的全屏模式
      setIsFullScreen(true);
      setIsPortalMode(true);
    }
  };

  const exitFullScreen = async () => {
    setToolbarVisible(false);
    setToolbarPinned(false);
    if (document.fullscreenElement || (document as any).webkitFullscreenElement) {
      try {
        if (document.exitFullscreen) {
          await document.exitFullscreen();
        } else if ((document as any).webkitExitFullscreen) {
          await (document as any).webkitExitFullscreen();
        }
      } catch {
        // ignore
      }
    }
    setIsFullScreen(false);
    setIsPortalMode(false);
  };

  const toggleFullScreen = () => {
    if (isFullScreen) {
      exitFullScreen();
    } else {
      enterFullScreen();
    }
  };

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center rounded-lg border bg-gray-50">
        <div className="text-center">
          <div className="inline-block h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent mb-2" />
          <p className="text-sm text-gray-500">正在生成安全预览沙箱会话...</p>
        </div>
      </div>
    );
  }

  if (error || !contentUrl) {
    return (
      <div className="flex h-96 flex-col items-center justify-center rounded-lg border border-red-200 bg-red-50 p-6 text-center">
        <div className="mb-2 text-2xl">⚠️</div>
        <p className="text-base font-semibold text-red-800">无法加载预览</p>
        <p className="mt-1 text-sm text-red-600">{error || '暂无可用预览内容'}</p>
        <button
          onClick={refresh}
          className="mt-4 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white shadow hover:bg-red-700"
        >
          重新尝试获取
        </button>
      </div>
    );
  }

  const renderToolbar = () => (
    <div className="flex h-12 shrink-0 items-center justify-between border-b bg-gray-50 px-4 py-2.5">
      <div className="flex items-center gap-3">
        <span className="text-sm font-semibold text-gray-800">{title}</span>
        <span className="rounded bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-800">
          沙箱隔离模式
        </span>
      </div>

      {/* Device Switcher */}
      <div className="flex items-center gap-1 rounded-lg border bg-white p-1 shadow-sm">
        <button
          onClick={() => setDeviceMode('responsive')}
          className={`rounded px-2.5 py-1 text-xs font-medium transition ${deviceMode === 'responsive' ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-100'}`}
        >
          自适应
        </button>
        <button
          onClick={() => setDeviceMode('desktop')}
          className={`rounded px-2.5 py-1 text-xs font-medium transition ${deviceMode === 'desktop' ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-100'}`}
        >
          桌面
        </button>
        <button
          onClick={() => setDeviceMode('tablet')}
          className={`rounded px-2.5 py-1 text-xs font-medium transition ${deviceMode === 'tablet' ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-100'}`}
        >
          平板
        </button>
        <button
          onClick={() => setDeviceMode('mobile')}
          className={`rounded px-2.5 py-1 text-xs font-medium transition ${deviceMode === 'mobile' ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-100'}`}
        >
          手机
        </button>
      </div>

      {/* Action Controls */}
      <div className="flex items-center gap-2">
        {isFullScreen && (
          <button
            onClick={() => setToolbarPinned((v) => !v)}
            title={toolbarPinned ? '取消固定工具栏' : '固定工具栏'}
            aria-pressed={toolbarPinned}
            className={`flex items-center gap-1 rounded border px-2.5 py-1 text-xs font-medium transition ${
              toolbarPinned
                ? 'border-blue-600 bg-blue-600 text-white hover:bg-blue-700'
                : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50'
            }`}
          >
            <svg
              viewBox="0 0 24 24"
              width="12"
              height="12"
              fill={toolbarPinned ? 'currentColor' : 'none'}
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M12 17v5" />
              <path d="M9 10.76a2 2 0 0 1-1.11 1.79l-1.8.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.8-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z" />
            </svg>
            {toolbarPinned ? '已固定' : '固定'}
          </button>
        )}
        <button
          onClick={refresh}
          title="刷新预览授权票据"
          className="rounded border border-gray-300 bg-white px-2.5 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50"
        >
          刷新票据
        </button>
        <button
          onClick={toggleFullScreen}
          className="rounded border border-gray-300 bg-white px-2.5 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50"
        >
          {isFullScreen ? '退出全屏' : '全屏预览'}
        </button>
      </div>
    </div>
  );

  const previewContent = (
    <div
      ref={containerRef}
      className={`prototype-preview flex flex-col rounded-lg border bg-white text-gray-900 [color-scheme:light] shadow-sm transition-all ${
        isFullScreen
          ? 'fixed inset-0 z-[9999] h-screen w-screen rounded-none border-0'
          : 'h-[calc(100vh-250px)] min-h-[640px] w-full'
      }`}
    >
      {/* Top Toolbar */}
      {isFullScreen ? (
        <>
          {/* 顶部触发热区：鼠标移入即展开浮层工具栏 */}
          <div
            className="absolute inset-x-0 top-0 z-[60] h-3"
            onMouseEnter={() => setToolbarVisible(true)}
          />
          {/* 浮层工具栏：绝对定位覆盖在内容之上，不占据下方空间 */}
          <div
            className={`absolute inset-x-0 top-0 z-50 shadow-lg will-change-transform transition-transform duration-300 ease-out ${
              toolbarVisible || toolbarPinned ? 'translate-y-0' : '-translate-y-full'
            }`}
            onMouseEnter={() => setToolbarVisible(true)}
            onMouseLeave={() => {
              if (!toolbarPinned) setToolbarVisible(false);
            }}
          >
            {renderToolbar()}
          </div>
        </>
      ) : (
        renderToolbar()
      )}

      {/* Frame Container */}
      <div className="prototype-frame-container flex flex-1 min-h-0 items-center justify-center overflow-auto bg-gray-200 p-4">
        <div
          className="h-full bg-white shadow-xl transition-all duration-300 flex flex-col"
          style={{ width: getContainerWidth() }}
        >
          <iframe
            title={title}
            src={contentUrl}
            sandbox="allow-scripts allow-forms allow-modals allow-downloads"
            referrerPolicy="no-referrer"
            allowFullScreen
            className="h-full w-full border-0 flex-1"
          />
        </div>
      </div>
    </div>
  );

  // 当降级为 Portal 模式全屏时，直接挂载到 document.body，彻底摆脱祖先样式（transform/backdrop-filter）限制
  if (isPortalMode && isFullScreen && typeof document !== 'undefined') {
    return createPortal(previewContent, document.body);
  }

  return previewContent;
};
