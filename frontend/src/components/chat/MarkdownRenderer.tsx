/* eslint-disable */

import * as React from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkCjkFriendly from "remark-cjk-friendly";
import rehypeRaw from "rehype-raw";
import rehypeSanitize from "rehype-sanitize";
import { Check, Copy, ImageIcon } from "lucide-react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneLight } from "react-syntax-highlighter/dist/esm/styles/prism";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * 文档预览用 Markdown 渲染器（从 ragent 裁剪引用角标 / 主题依赖，保留预览所需能力）
 */
interface MarkdownRendererProps {
  content: string;
  messageId?: string;
  sources?: unknown[];
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = React.useState(false);
  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      className="h-6 px-2 text-[11px] text-[#57606a] hover:text-[#24292f]"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          window.setTimeout(() => setCopied(false), 1500);
        } catch {
          /* ignore */
        }
      }}
    >
      {copied ? <Check className="mr-1 h-3 w-3" /> : <Copy className="mr-1 h-3 w-3" />}
      {copied ? "已复制" : "复制"}
    </Button>
  );
}

export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  return (
    <div className="prose prose-sm max-w-none dark:prose-invert">
      <ReactMarkdown
        remarkPlugins={[[remarkGfm, { singleTilde: false }], remarkCjkFriendly]}
        rehypePlugins={[rehypeRaw, rehypeSanitize]}
        components={{
          code({ className, children, ...props }) {
            const match = /language-(\w+)/.exec(className || "");
            const language = match?.[1] || "text";
            const value = String(children).replace(/\n$/, "");

            if (!value.includes("\n")) {
              return (
                <code
                  className={cn(
                    "mx-0.5 rounded px-1.5 py-0.5 text-[13px] font-mono bg-[#f6f8fa] text-[#24292f]",
                    className
                  )}
                  {...props}
                >
                  {children}
                </code>
              );
            }

            return (
              <div className="my-3 overflow-hidden rounded-md border border-[#d0d7de] bg-[#f6f8fa]">
                <div className="flex items-center justify-between border-b border-[#d0d7de] bg-[#f6f8fa] px-3 py-1.5">
                  <span className="font-mono text-[11px] font-semibold uppercase tracking-wider text-[#57606a]">
                    {language}
                  </span>
                  <CopyButton value={value} />
                </div>
                <div className="overflow-x-auto">
                  <SyntaxHighlighter
                    language={language}
                    style={oneLight}
                    PreTag="div"
                    customStyle={{
                      margin: 0,
                      padding: "0.75rem 1rem",
                      background: "transparent",
                      fontSize: "13px",
                      lineHeight: "1.5"
                    }}
                    showLineNumbers={false}
                    wrapLines={true}
                  >
                    {value}
                  </SyntaxHighlighter>
                </div>
              </div>
            );
          },
          img({ src, alt, ...props }) {
            const [hasError, setHasError] = React.useState(false);
            if (hasError) {
              return (
                <div className="my-3 flex items-center gap-2 text-sm text-[#999999]">
                  <ImageIcon className="h-4 w-4" />
                  <span>图片加载失败</span>
                </div>
              );
            }
            return (
              <img
                src={src}
                alt={alt || ""}
                className="my-3 max-w-full rounded-lg"
                onError={() => setHasError(true)}
                loading="lazy"
                {...props}
              />
            );
          },
          a({ href, children, ...props }) {
            return (
              <a href={href} target="_blank" rel="noreferrer" {...props}>
                {children}
              </a>
            );
          },
          table({ children }) {
            return (
              <div className="my-4 overflow-x-auto">
                <table className="w-full border-collapse text-sm">{children}</table>
              </div>
            );
          },
          th({ children }) {
            return (
              <th className="border border-[#d0d7de] bg-[#f6f8fa] px-3 py-2 text-left font-semibold">
                {children}
              </th>
            );
          },
          td({ children }) {
            return <td className="border border-[#d0d7de] px-3 py-2 align-top">{children}</td>;
          }
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
