import { useState } from 'react';
import { Alert, Badge, Button, Input, Tag, useToast } from '@fj';
import { Camera, Check, Pencil, ShieldCheck } from 'lucide-react';
import { describeError, logout } from '../lib/api';
import { avatarInitial } from '../lib/format';
import { IDENTITY_STATUS_LABEL, IDENTITY_STATUS_TONE, LIVENESS_STATUS_LABEL } from '../lib/labels';
import { useIdentityVerificationQuery, useStartIdentityVerification, useSubmitDriverCase } from '../lib/queries';
import { useSession } from '../lib/session';
import type { Session, VerificationState } from '../lib/types';

/** 我的 — profile header + 成为车主 stepper (A4) + logout. */
export function ProfileScreen({ session }: { session: Session }) {
  const setSession = useSession((state) => state.setSession);

  return (
    <div className="screen">
      <header className="screen-header">
        <span className="page-title">我的</span>
      </header>

      <div className="screen-body">
        <section className="panel profile-head">
          <span className="avatar avatar-lg">{avatarInitial(session.user.phone)}</span>
          <div className="profile-meta">
            <strong>{session.user.phone}</strong>
            <div className="profile-roles">
              {session.user.roles.map((role) => (
                <Badge key={role} tone={role === 'DRIVER' ? 'success' : 'neutral'}>{role}</Badge>
              ))}
            </div>
          </div>
        </section>

        <DriverOnboardingCard />

        <Button full variant="ghost" onClick={() => logout(session, setSession)}>退出登录</Button>
      </div>
    </div>
  );
}

/** A4 · 成为车主 — 实名 → 活体/审批 → 证件 → 审核 stepper over the real identity + driver-case APIs. */
function DriverOnboardingCard() {
  const toast = useToast();
  const [verificationId, setVerificationId] = useState<string | null>(null);
  const [realName, setRealName] = useState('');
  const [idNumber, setIdNumber] = useState('');
  const [drivingLicenseFile, setDrivingLicenseFile] = useState<File | null>(null);
  const [vehicleLicenseFile, setVehicleLicenseFile] = useState<File | null>(null);
  const [verificationState, setVerificationState] = useState<VerificationState>('DRAFT');
  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const start = useStartIdentityVerification({
    onSuccess: (verification) => {
      setVerificationId(verification.verificationId);
      toast({ title: '实名认证已发起，请完成活体检测', tone: 'info' });
    },
    onError: showError
  });

  const verificationQuery = useIdentityVerificationQuery(verificationId);
  const verification = verificationQuery.data;
  const identityApproved = verification?.status === 'APPROVED';

  const submitVerification = useSubmitDriverCase({
    onSuccess: (result) => {
      setVerificationState(result.status);
      toast({ title: 'OCR Mock 已识别，等待后台复核', tone: 'success' });
    },
    onError: showError
  });

  const docsSubmitted = verificationState !== 'DRAFT';
  const step = !verificationId ? 1 : !identityApproved ? 2 : !docsSubmitted ? 3 : 4;

  return (
    <section className="panel onboarding">
      <div className="onboarding-head">
        <ShieldCheck size={18} color="var(--accent)" />
        <span className="panel-title">成为车主</span>
      </div>

      <div className="step-rail">
        <StepDot index={1} label="实名" state={step > 1 ? 'done' : 'current'} />
        <span className={`step-link${step > 1 ? ' done' : ''}`} />
        <StepDot index={2} label="活体" state={step > 2 ? 'done' : step === 2 ? 'current' : 'pending'} />
        <span className={`step-link${step > 2 ? ' done' : ''}`} />
        <StepDot index={3} label="证件" state={step > 3 ? 'done' : step === 3 ? 'current' : 'pending'} />
        <span className={`step-link${step > 3 ? ' done' : ''}`} />
        <StepDot index={4} label="审核" state={verificationState === 'APPROVED' ? 'done' : step === 4 ? 'current' : 'pending'} />
      </div>

      <div className="step-eyebrow">STEP {step} / 4</div>

      {step === 1 && (
        <>
          <h3 className="step-title">实名信息</h3>
          <Input label="真实姓名" value={realName} onChange={(event) => setRealName(event.target.value)} />
          <Input label="证件号" inputMode="numeric" value={idNumber} onChange={(event) => setIdNumber(event.target.value)} />
          <Button
            full
            variant="primary"
            disabled={!realName || !idNumber || start.isPending}
            onClick={() => start.mutate({ realName, idNumber })}
          >
            {start.isPending ? '发起中…' : '发起实名认证'}
          </Button>
        </>
      )}

      {step === 2 && (
        <>
          <h3 className="step-title">等待认证结果</h3>
          <div className="status-line">
            <Badge tone={verification ? IDENTITY_STATUS_TONE[verification.status] : 'accent'}>
              {verification ? IDENTITY_STATUS_LABEL[verification.status] : '查询中…'}
            </Badge>
            {verification && <Tag accent="neutral">{LIVENESS_STATUS_LABEL[verification.livenessStatus]}</Tag>}
          </div>
          <Alert tone="info" title="结果由供应商回调驱动">
            认证与活体结果异步投递到收件箱（演示中由运营在后台驱动活体 PASS 与会话 APPROVED），此处状态自动刷新。
          </Alert>
        </>
      )}

      {step === 3 && (
        <>
          <h3 className="step-title">上传证件</h3>
          <p className="step-sub">OCR 自动识别后转人工复核。照片需清晰、四角完整。</p>
          <UploadCard label="驾驶证" file={drivingLicenseFile} onPick={setDrivingLicenseFile} />
          <UploadCard label="行驶证" file={vehicleLicenseFile} onPick={setVehicleLicenseFile} />
          <div className="ocr-line">
            <span>OCR 状态</span>
            <Tag accent={docsSubmitted ? 'coral' : 'neutral'}>{verificationState}</Tag>
          </div>
          <Button
            full
            variant="primary"
            size="lg"
            disabled={!drivingLicenseFile || !vehicleLicenseFile || submitVerification.isPending}
            onClick={() => submitVerification.mutate({ drivingLicenseFile, vehicleLicenseFile })}
          >
            {submitVerification.isPending ? '提交中…' : '提交审核'}
          </Button>
        </>
      )}

      {step === 4 && (
        <>
          <h3 className="step-title">平台审核</h3>
          {verificationState === 'APPROVED' ? (
            <Alert tone="success" title="审核已通过">您已获得车主能力，可以发布行程了。</Alert>
          ) : (
            <Alert tone="info" title="等待运营复核">
              证件已提交（{verificationState}），OCR 识别完成后由运营人工复核，结果会投递到收件箱。
            </Alert>
          )}
        </>
      )}
    </section>
  );
}

function StepDot({ index, label, state }: { index: number; label: string; state: 'done' | 'current' | 'pending' }) {
  return (
    <div className={`step-dot ${state}`}>
      <span className="step-dot-circle">{state === 'done' ? <Check size={15} /> : index}</span>
      <span className="step-dot-label">{label}</span>
    </div>
  );
}

/** Design-style upload slot: dashed 待上传 → solid success once a file is picked. */
function UploadCard({ label, file, onPick }: { label: string; file: File | null; onPick: (file: File | null) => void }) {
  return (
    <label className={`upload-card${file ? ' done' : ''}`}>
      <span className="upload-icon">{file ? <Check size={20} /> : <Camera size={20} />}</span>
      <span className="upload-meta">
        <strong>{label}</strong>
        <span>{file ? `${file.name} · 待提交` : '点击拍摄或从相册选择'}</span>
      </span>
      {file ? <Pencil size={17} className="upload-edit" /> : <span className="upload-action">上传</span>}
      <input type="file" accept="image/*,.pdf" onChange={(event) => onPick(event.target.files?.[0] ?? null)} />
    </label>
  );
}
