import { NextResponse } from "next/server";

import { buildAuthHeaders, resolveBackendUrl, type ApiEnvelope } from "@aioj/api";
import { getServerAccessToken } from "../../../lib/server-auth";

export async function GET() {
  const token = await getServerAccessToken();
  if (!token) return NextResponse.json({ message: "请先登录。" }, { status: 401 });
  const response = await fetch(resolveBackendUrl("/friend/resume/list"), {
    headers: buildAuthHeaders(token),
    cache: "no-store"
  });
  return NextResponse.json(await response.json(), { status: response.status });
}

export async function POST(request: Request) {
  const token = await getServerAccessToken();
  if (!token) return NextResponse.json({ message: "请先登录。" }, { status: 401 });
  const incoming = await request.formData();
  const file = incoming.get("file");
  if (!(file instanceof File)) return NextResponse.json({ message: "请选择 PDF、DOC 或 DOCX 简历。" }, { status: 400 });
  const form = new FormData();
  form.append("file", file, file.name);
  const response = await fetch(resolveBackendUrl("/friend/resume/upload"), {
    method: "POST",
    headers: buildAuthHeaders(token),
    body: form,
    cache: "no-store"
  });
  const payload = (await response.json()) as ApiEnvelope<unknown>;
  return NextResponse.json(payload, { status: response.status });
}
