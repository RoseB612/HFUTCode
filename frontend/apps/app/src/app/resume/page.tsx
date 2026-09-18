"use client";

import { useEffect, useState } from "react";
import { FileText, LoaderCircle, Upload } from "lucide-react";
import { Button, Panel, Tag } from "@aioj/ui";
import { appApiPath } from "../../lib/paths";
import { appPublicPath } from "../../lib/paths";

type ResumeItem = { resumeId: number; originalFilename?: string; summary?: string; score?: number; createTime?: string };

export default function ResumePage() {
  const [items, setItems] = useState<ResumeItem[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("上传 PDF/DOC/DOCX 后，系统会解析内容并给出简历建议。");

  useEffect(() => {
    fetch(appApiPath("/resume"), { cache: "no-store" }).then(async (response) => {
      if (response.ok) setItems(((await response.json())?.data ?? []) as ResumeItem[]);
    }).catch(() => undefined);
  }, []);

  async function upload(file: File) {
    setBusy(true); setMessage("正在上传并分析简历，请稍候…");
    const form = new FormData(); form.append("file", file);
    try {
      const response = await fetch(appApiPath("/resume"), { method: "POST", body: form });
      const payload = await response.json();
      if (!response.ok) throw new Error(payload?.message || "简历上传失败");
      setMessage("分析完成，已保存到你的简历记录。");
      setItems((current) => [payload.data, ...current]);
    } catch (error) { setMessage(error instanceof Error ? error.message : "简历上传失败"); }
    finally { setBusy(false); }
  }

  return <div className="space-y-6">
    <Panel tone="strong" className="p-6"><p className="text-sm text-[var(--text-muted)]">Career workspace</p><h1 className="mt-2 text-4xl font-semibold">简历分析</h1><p className="mt-3 max-w-2xl text-sm leading-7 text-[var(--text-secondary)]">上传你的简历，获得结构、内容和岗位匹配建议。文件仅用于当前账号的分析记录。</p>
      <label className="mt-6 flex cursor-pointer flex-col items-center justify-center rounded-[20px] border border-dashed border-[var(--border-soft)] bg-[var(--surface-3)] px-6 py-12 text-center hover:bg-[var(--surface-1)]"><Upload size={24} /><span className="mt-3 font-medium">选择简历文件</span><span className="mt-1 text-xs text-[var(--text-muted)]">PDF / DOC / DOCX，最大 10MB</span><input className="hidden" type="file" accept=".pdf,.doc,.docx" disabled={busy} onChange={(event) => { const file = event.target.files?.[0]; if (file) void upload(file); }} /></label>
      <p className="text-sm text-[var(--text-muted)]">{busy ? <LoaderCircle className="mr-2 inline animate-spin" size={14} /> : null}{message}</p>
    </Panel>
    <Panel className="p-6"><div className="flex items-center justify-between"><h2 className="text-xl font-semibold">我的简历记录</h2><Tag tone="default">{items.length} 份</Tag></div>{items.length === 0 ? <p className="mt-6 text-sm text-[var(--text-muted)]">还没有上传记录，先上传一份简历吧。</p> : <div className="mt-5 space-y-3">{items.map((item) => <div key={item.resumeId} className="flex items-center gap-3 rounded-[16px] border border-[var(--border-soft)] p-4"><FileText size={18} /><div className="min-w-0 flex-1"><p className="truncate font-medium">{item.originalFilename || `简历 #${item.resumeId}`}</p><p className="mt-1 truncate text-xs text-[var(--text-muted)]">{item.summary || "已完成分析"}</p></div>{item.score != null ? <Tag tone="accent">{item.score} 分</Tag> : null}</div>)}</div>}</Panel>
    <p className="text-sm text-[var(--text-muted)]">想开始练习面试？前往 <a className="text-[var(--accent)]" href={appPublicPath("/interview")}>模拟面试</a>。</p>
  </div>;
}
