import type { CodeLanguage, Difficulty } from "./contracts";

export const DEFAULT_BACKEND_BASE_URL = "http://localhost:19090";
export const DEFAULT_BACKEND_SERVICE_PREFIX = "/friend";

export function resolveBackendBaseUrl(
  baseUrl = typeof window === "undefined"
    ? process.env.SYNCODE_BACKEND_BASE_URL ??
      process.env.NEXT_PUBLIC_BACKEND_BASE_URL ??
      DEFAULT_BACKEND_BASE_URL
    : process.env.NEXT_PUBLIC_BACKEND_BASE_URL ??
      process.env.SYNCODE_BACKEND_BASE_URL ??
      DEFAULT_BACKEND_BASE_URL
) {
  return baseUrl.replace(/\/+$/, "");
}

export function resolveBackendServicePrefix(
  servicePrefix = typeof window === "undefined"
    ? process.env.SYNCODE_BACKEND_SERVICE_PREFIX ??
      process.env.NEXT_PUBLIC_BACKEND_SERVICE_PREFIX ??
      DEFAULT_BACKEND_SERVICE_PREFIX
    : process.env.NEXT_PUBLIC_BACKEND_SERVICE_PREFIX ??
      process.env.SYNCODE_BACKEND_SERVICE_PREFIX ??
      DEFAULT_BACKEND_SERVICE_PREFIX
) {
  const trimmed = servicePrefix.trim().replace(/^\/+|\/+$/g, "");
  return trimmed ? `/${trimmed}` : "";
}

export function resolveBackendServicePath(
  path: string,
  servicePrefix = resolveBackendServicePrefix()
) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  if (normalizedPath === DEFAULT_BACKEND_SERVICE_PREFIX) {
    return servicePrefix || "/";
  }
  if (normalizedPath.startsWith(`${DEFAULT_BACKEND_SERVICE_PREFIX}/`)) {
    const servicePath = normalizedPath.slice(DEFAULT_BACKEND_SERVICE_PREFIX.length);
    return `${servicePrefix}${servicePath}`;
  }
  return normalizedPath;
}

export function resolveBackendUrl(path: string, baseUrl = resolveBackendBaseUrl()) {
  return `${baseUrl}${resolveBackendServicePath(path)}`;
}

export function resolveJudgeWebSocketUrl(baseUrl = resolveBackendBaseUrl()) {
  const wsBase = baseUrl.replace(/^http:\/\//, "ws://").replace(/^https:\/\//, "wss://");
  return `${wsBase}${resolveBackendServicePath("/friend/ws/judge/result")}`;
}

export function normalizeDifficulty(difficulty?: number | string | null): Difficulty {
  if (difficulty === 1 || difficulty === "1" || difficulty === "Easy") return "Easy";
  if (difficulty === 2 || difficulty === "2" || difficulty === "Medium") return "Medium";
  if (difficulty === 3 || difficulty === "3" || difficulty === "Hard") return "Hard";
  return "Medium";
}

export function isJudgeLanguageSupported(language: CodeLanguage) {
  return language === "java";
}

export function programTypeFromLanguage(language: CodeLanguage) {
  if (language === "java") return 0;
  if (language === "cpp") return 1;
  if (language === "go") return 2;
  throw new Error(`Unsupported judge language: ${language}`);
}
