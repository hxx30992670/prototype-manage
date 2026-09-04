import React from 'react';
import { StageBackdrop } from '@/components/visual/StageBackdrop';
import { LoginFormPanel } from './LoginFormPanel';
import { LoginHero } from './LoginHero';
import { useLogin } from './useLogin';

export const LoginPage: React.FC = () => {
  const { loading, errorMessage, captcha, returnUrl, loadCaptcha, handleSubmit } = useLogin();

  return (
    <StageBackdrop density="login">
      <div className="grid min-h-screen lg:grid-cols-[minmax(0,1.15fr)_minmax(360px,0.85fr)]">
        <LoginHero />
        <div className="flex items-center justify-center px-5 py-10 lg:px-12">
          <LoginFormPanel
            loading={loading}
            errorMessage={errorMessage}
            captcha={captcha}
            returnUrl={returnUrl}
            onSubmit={handleSubmit}
            onReloadCaptcha={loadCaptcha}
          />
        </div>
      </div>
    </StageBackdrop>
  );
};
