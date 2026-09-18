import { Mic2, MessageSquareText, Sparkles } from "lucide-react";
import { Button, Panel, Tag } from "@aioj/ui";
import { appPublicPath } from "../../lib/paths";

export default function InterviewPage() {
  return <div className="space-y-6"><Panel tone="strong" className="p-6"><Tag tone="accent">Interview workspace</Tag><h1 className="mt-3 text-4xl font-semibold">模拟面试</h1><p className="mt-3 max-w-2xl text-sm leading-7 text-[var(--text-secondary)]">按岗位进行自我介绍、项目追问和算法问答训练。面试题库和 AI 追问界面已预留，语音实时面试服务正在接入中。</p><div className="mt-6 grid gap-4 md:grid-cols-3"><Panel className="p-5"><MessageSquareText size={20} /><h2 className="mt-4 font-semibold">项目面</h2><p className="mt-2 text-sm text-[var(--text-muted)]">围绕简历项目准备高频追问。</p></Panel><Panel className="p-5"><Sparkles size={20} /><h2 className="mt-4 font-semibold">AI 追问</h2><p className="mt-2 text-sm text-[var(--text-muted)]">后端能力接通后自动生成追问和反馈。</p></Panel><Panel className="p-5"><Mic2 size={20} /><h2 className="mt-4 font-semibold">语音面试</h2><p className="mt-2 text-sm text-[var(--text-muted)]">语音通话能力尚未上线。</p></Panel></div><div className="mt-6 flex gap-3"><a href={appPublicPath("/resume")}><Button variant="secondary">先完善简历</Button></a><a href={appPublicPath("/problems")}><Button>练习算法题</Button></a></div></Panel></div>;
}
