import { NextResponse } from "next/server";

import { ACCESS_TOKEN_KEY, requestJson, unwrapData } from "@aioj/api";
import { resolveApiRouteError } from "../../../../lib/api-route-error";

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const payload = await requestJson<{ code: number; msg: string; data: string }>("/friend/user/register", {
      method: "POST",
      body: JSON.stringify(body)
    });

    const token = unwrapData(payload);
    const response = NextResponse.json({ token });
    response.cookies.set({
      name: ACCESS_TOKEN_KEY,
      value: token,
      httpOnly: true,
      sameSite: "lax",
      path: "/"
    });
    return response;
  } catch (error) {
    const { status, body } = resolveApiRouteError(error, "注册失败。");
    return NextResponse.json(body, { status });
  }
}
