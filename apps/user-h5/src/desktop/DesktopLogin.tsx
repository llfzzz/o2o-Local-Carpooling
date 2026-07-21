import { useState } from 'react';
import { Badge, Button, Card, Input, Tag, Text, useToast } from '@fj';
import { CarFront } from 'lucide-react';
import { describeError } from '../lib/api';
import { useDemoPeekLoginCode, useLogin, useSendSmsCode } from '../lib/queries';

/** Desktop login — centered card, same interactive SMS flow as mobile: the code is never
 *  auto-filled; in demo mode it can be peeked here (and only here) via the send challenge. */
export function DesktopLogin() {
  const toast = useToast();
  const [phone, setPhone] = useState('13800000000');
  const [code, setCode] = useState('');
  const [challengeId, setChallengeId] = useState<string | null>(null);
  const [demoCode, setDemoCode] = useState<string | null>(null);

  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const sendCode = useSendSmsCode(phone, {
    onSuccess: (response) => {
      setChallengeId(response.challengeId);
      setDemoCode(null);
      toast({ title: response.message, tone: 'success' });
    },
    onError: showError
  });

  const peekDemoCode = useDemoPeekLoginCode(phone, challengeId, {
    onSuccess: (response) => {
      setDemoCode(response.code);
      toast({ title: response.code ? '演示验证码已取出，登录后即失效' : response.message, tone: response.code ? 'success' : 'info' });
    },
    onError: showError
  });

  const login = useLogin(phone, code, {
    onSuccess: () => {
      setDemoCode(null);
      setChallengeId(null);
      toast({ title: '登录成功', tone: 'success' });
    },
    onError: showError
  });

  return (
    <main className="dsk-login">
      <Card padding="28px" style={{ width: '100%', maxWidth: 420 }}>
        <div className="dsk-login-card">
          <div className="dsk-login-brand">
            <span className="dsk-sider-brand-mark"><CarFront size={17} /></span>
            <div className="dsk-sider-brand-copy">
              <span className="dsk-sider-brand-name">同城拼车</span>
              <span className="dsk-sider-brand-sub">RIDER · TRIP FLOW</span>
            </div>
          </div>

          <div>
            <Text variant="eyebrow" as="div">FREE JOY · 验证码登录</Text>
            <h1 className="dsk-login-headline">顺路的人，一起走。</h1>
            <p className="dsk-login-sub">同城通勤拼车。输入手机号，用验证码快速登录。</p>
          </div>

          <Input label="手机号" inputMode="numeric" value={phone} onChange={(event) => setPhone(event.target.value)} />
          <div className="dsk-code-row">
            <div className="dsk-code-row-input">
              <Input label="验证码" inputMode="numeric" placeholder="6 位验证码" value={code} onChange={(event) => setCode(event.target.value)} />
            </div>
            <Button variant="secondary" disabled={sendCode.isPending} onClick={() => sendCode.mutate()}>
              {sendCode.isPending ? '发送中…' : '获取验证码'}
            </Button>
          </div>

          <Button
            full
            variant="primary"
            size="lg"
            disabled={!challengeId || !code || login.isPending}
            onClick={() => login.mutate()}
          >
            {login.isPending ? '登录中…' : '登录'}
          </Button>

          <div className="dsk-login-demo-row">
            <Tag accent="bloom">演示</Tag>
            <span>验证码仅在本页临时可见</span>
            {challengeId && (
              <Button variant="ghost" size="sm" disabled={peekDemoCode.isPending} onClick={() => peekDemoCode.mutate()}>
                {peekDemoCode.isPending ? '读取中…' : '查看演示验证码'}
              </Button>
            )}
          </div>
          {demoCode && (
            <div className="dsk-status-line" style={{ justifyContent: 'center' }}>
              <Badge tone="accent">演示验证码</Badge>
              <span className="dsk-mono">{demoCode}</span>
            </div>
          )}

          <p className="dsk-login-terms">登录即代表同意《服务协议》与《隐私政策》</p>
        </div>
      </Card>
    </main>
  );
}
