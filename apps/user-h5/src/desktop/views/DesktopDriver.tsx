import { useState } from 'react';
import { Alert, Badge, Button, Card, Input, Stack, Tag, Timeline, useToast } from '@fj';
import type { TimelineItem } from '@fj';
import { Camera, Check } from 'lucide-react';
import { describeError } from '../../lib/api';
import { IDENTITY_STATUS_LABEL, IDENTITY_STATUS_TONE, LIVENESS_STATUS_LABEL } from '../../lib/labels';
import { useIdentityVerificationQuery, useStartIdentityVerification, useSubmitDriverCase } from '../../lib/queries';
import type { VerificationState } from '../../lib/types';

const DONE = 'var(--success-500)';
const CURRENT = 'var(--accent)';
const PENDING = 'var(--border-strong)';
const DANGER = 'var(--danger-500)';

/** 成为车主 — the A4 onboarding funnel as a dedicated desktop view: horizontal stepper +
 *  current-step card on the left, live verification status timeline on the right. */
export function DesktopDriver() {
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

  const livenessBad = verification && (verification.livenessStatus === 'FAILED' || verification.livenessStatus === 'TIMEOUT');
  // Icon-less items render as solid accent dots — state reads from the color alone.
  const statusTimeline: TimelineItem[] = [
    {
      title: '实名认证',
      body: verification ? IDENTITY_STATUS_LABEL[verification.status] : verificationId ? '查询中…' : '填写姓名与证件号发起',
      accent: identityApproved ? DONE : verification && verification.status !== 'PENDING' ? DANGER : CURRENT
    },
    {
      title: '活体检测',
      body: verification ? LIVENESS_STATUS_LABEL[verification.livenessStatus] : '实名发起后进行',
      accent: verification?.livenessStatus === 'PASSED' ? DONE : livenessBad ? DANGER : step === 2 ? CURRENT : PENDING
    },
    {
      title: '证件上传 · OCR',
      body: docsSubmitted ? `已提交（${verificationState}）` : '驾驶证 + 行驶证',
      accent: docsSubmitted ? DONE : step === 3 ? CURRENT : PENDING
    },
    {
      title: '平台审核',
      body: verificationState === 'APPROVED' ? '审核已通过，车主能力已开通' : '运营人工复核，结果投递收件箱',
      accent: verificationState === 'APPROVED' ? DONE : step === 4 ? CURRENT : PENDING
    }
  ];

  return (
    <div className="dsk-driver-grid">
      <Card padding="24px">
        <Stack gap={16}>
          <div className="dsk-step-rail">
            <StepDot index={1} label="实名" state={step > 1 ? 'done' : 'current'} />
            <span className={`dsk-step-link${step > 1 ? ' done' : ''}`} />
            <StepDot index={2} label="活体" state={step > 2 ? 'done' : step === 2 ? 'current' : 'pending'} />
            <span className={`dsk-step-link${step > 2 ? ' done' : ''}`} />
            <StepDot index={3} label="证件" state={step > 3 ? 'done' : step === 3 ? 'current' : 'pending'} />
            <span className={`dsk-step-link${step > 3 ? ' done' : ''}`} />
            <StepDot index={4} label="审核" state={verificationState === 'APPROVED' ? 'done' : step === 4 ? 'current' : 'pending'} />
          </div>

          <span className="dsk-step-eyebrow">STEP {step} / 4</span>

          {step === 1 && (
            <>
              <h3 className="dsk-step-title">实名信息</h3>
              <Input label="真实姓名" value={realName} onChange={(event) => setRealName(event.target.value)} />
              <Input label="证件号" inputMode="numeric" value={idNumber} onChange={(event) => setIdNumber(event.target.value)} />
              <Button
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
              <h3 className="dsk-step-title">等待认证结果</h3>
              <div className="dsk-status-line">
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
              <h3 className="dsk-step-title">上传证件</h3>
              <p className="dsk-step-sub">OCR 自动识别后转人工复核。照片需清晰、四角完整。</p>
              <div className="dsk-doc-grid">
                <DocSlot label="驾驶证" file={drivingLicenseFile} onPick={setDrivingLicenseFile} />
                <DocSlot label="行驶证" file={vehicleLicenseFile} onPick={setVehicleLicenseFile} />
              </div>
              <div className="dsk-price-row">
                <span>OCR 状态</span>
                <Tag accent={docsSubmitted ? 'coral' : 'neutral'}>{verificationState}</Tag>
              </div>
              <Button
                variant="primary"
                disabled={!drivingLicenseFile || !vehicleLicenseFile || submitVerification.isPending}
                onClick={() => submitVerification.mutate({ drivingLicenseFile, vehicleLicenseFile })}
              >
                {submitVerification.isPending ? '提交中…' : '提交审核'}
              </Button>
            </>
          )}

          {step === 4 && (
            <>
              <h3 className="dsk-step-title">平台审核</h3>
              {verificationState === 'APPROVED' ? (
                <Alert tone="success" title="审核已通过">您已获得车主能力，可以发布行程了。</Alert>
              ) : (
                <Alert tone="info" title="等待运营复核">
                  证件已提交（{verificationState}），OCR 识别完成后由运营人工复核，结果会投递到收件箱。
                </Alert>
              )}
            </>
          )}
        </Stack>
      </Card>

      <Card padding="24px">
        <Stack gap={16}>
          <span className="dsk-section-title">认证进度</span>
          <Timeline items={statusTimeline} />
          <Alert tone="info" title="异步回调驱动">
            全流程状态由供应商回调与运营复核驱动，此页 4 秒自动刷新，结果同时投递到「消息」收件箱。
          </Alert>
        </Stack>
      </Card>
    </div>
  );
}

function StepDot({ index, label, state }: { index: number; label: string; state: 'done' | 'current' | 'pending' }) {
  return (
    <div className={`dsk-step-dot ${state}`}>
      <span className="dsk-step-dot-circle">{state === 'done' ? <Check size={15} /> : index}</span>
      <span className="dsk-step-dot-label">{label}</span>
    </div>
  );
}

/** Desktop upload slot: dashed 待上传 → solid success once a file is picked. */
function DocSlot({ label, file, onPick }: { label: string; file: File | null; onPick: (file: File | null) => void }) {
  return (
    <label className={`dsk-doc-slot${file ? ' done' : ''}`}>
      {file ? <Check size={20} /> : <Camera size={20} />}
      <strong>{label}</strong>
      <span className="dsk-doc-name">{file ? `${file.name} · 待提交` : '点击选择照片或 PDF'}</span>
      <input type="file" accept="image/*,.pdf" onChange={(event) => onPick(event.target.files?.[0] ?? null)} />
    </label>
  );
}
