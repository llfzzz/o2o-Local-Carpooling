import { useState } from 'react';
import { Badge, Button, Input, Tag, Text, useToast } from '@fj';
import { describeError } from '../lib/api';
import { useLogin, usePeekDemoInbox, useSendSmsCode } from '../lib/queries';

/** A1 · 登录 — hero copy + phone/code, code fetched from the demo inbox by explicit action. */
export function LoginScreen() {
  const toast = useToast();
  const [phone, setPhone] = useState('13800000000');
  const [code, setCode] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [demoCode, setDemoCode] = useState<string | null>(null);

  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const sendCode = useSendSmsCode(phone, {
    onSuccess: (response) => {
      setCodeSent(true);
      setDemoCode(null);
      toast({ title: response.message, tone: 'success' });
    },
    onError: showError
  });

  const peekDemoInbox = usePeekDemoInbox(phone, {
    onSuccess: (response) => {
      setDemoCode(response.code);
      toast({ title: response.code ? '已从演示收件箱取出验证码' : response.message, tone: response.code ? 'success' : 'info' });
    },
    onError: showError
  });

  const login = useLogin(phone, code, {
    onSuccess: () => toast({ title: '登录成功', tone: 'success' }),
    onError: showError
  });

  return (
    <main className="mobile-shell login-screen">
      <div className="login-body">
        <div className="brand-row">
          <span className="brand-dot" />
          <span className="brand-name">同城拼车</span>
        </div>

        <div className="login-hero">
          <Text variant="eyebrow" as="div">FREE JOY · 验证码登录</Text>
          <h1 className="login-headline">顺路的人，<br />一起走。</h1>
          <p className="login-sub">同城通勤拼车。输入手机号，用验证码快速登录。</p>
        </div>

        <div className="login-spacer" />

        <Input label="手机号" inputMode="numeric" value={phone} onChange={(event) => setPhone(event.target.value)} />
        <div className="code-row">
          <div className="code-row-input">
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
          disabled={!codeSent || !code || login.isPending}
          onClick={() => login.mutate()}
        >
          {login.isPending ? '登录中…' : '登录'}
        </Button>

        <div className="login-demo-row">
          <Tag accent="bloom">演示</Tag>
          <span>验证码会写入演示收件箱</span>
          {codeSent && (
            <Button variant="ghost" size="sm" disabled={peekDemoInbox.isPending} onClick={() => peekDemoInbox.mutate()}>
              {peekDemoInbox.isPending ? '读取中…' : '查看演示验证码'}
            </Button>
          )}
        </div>
        {demoCode && (
          <div className="status-line" style={{ justifyContent: 'center' }}>
            <Badge tone="accent">演示验证码</Badge>
            <span className="mono">{demoCode}</span>
          </div>
        )}

        <p className="login-terms">登录即代表同意《服务协议》与《隐私政策》</p>
      </div>
    </main>
  );
}
