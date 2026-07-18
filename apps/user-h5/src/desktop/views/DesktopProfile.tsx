import { Badge, Button, Card, Stack } from '@fj';
import { ShieldCheck } from 'lucide-react';
import { logout } from '../../lib/api';
import { avatarInitial, shortId } from '../../lib/format';
import { useSession } from '../../lib/session';
import type { Session } from '../../lib/types';

/** 个人中心 — account card + facts + driver-capability entry + logout. */
export function DesktopProfile({ session, onGoDriver }: { session: Session; onGoDriver: () => void }) {
  const setSession = useSession((state) => state.setSession);
  const isDriver = session.user.roles.includes('DRIVER');

  return (
    <div className="dsk-profile-col">
      <Card padding="24px">
        <Stack gap={16}>
          <div className="dsk-profile-head">
            <span className="dsk-sider-avatar lg">{avatarInitial(session.user.phone)}</span>
            <div className="dsk-profile-meta">
              <strong>{session.user.phone}</strong>
              <div className="dsk-profile-roles">
                {session.user.roles.map((role) => (
                  <Badge key={role} tone={role === 'DRIVER' ? 'success' : 'neutral'}>{role}</Badge>
                ))}
              </div>
            </div>
          </div>

          <div className="dsk-facts">
            <div className="dsk-fact">
              <span>用户 ID</span>
              <span className="dsk-mono" title={session.user.userId}>USR·{shortId(session.user.userId)}</span>
            </div>
            <div className="dsk-fact">
              <span>手机号</span>
              <span className="dsk-mono">{session.user.phone}</span>
            </div>
            <div className="dsk-fact">
              <span>车主能力</span>
              <Badge tone={isDriver ? 'success' : 'neutral'}>{isDriver ? '已开通' : '未开通'}</Badge>
            </div>
          </div>

          <Stack direction="row" gap={10}>
            <Button variant="secondary" iconLeft={<ShieldCheck size={16} />} onClick={onGoDriver}>
              {isDriver ? '查看车主认证' : '去认证成为车主'}
            </Button>
            <Button variant="ghost" onClick={() => logout(session, setSession)}>退出登录</Button>
          </Stack>
        </Stack>
      </Card>
    </div>
  );
}
