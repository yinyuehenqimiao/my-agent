import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-4">
      <h1 className="font-display text-3xl font-semibold">404</h1>
      <p className="text-sm text-muted-foreground">页面不存在</p>
      <Button asChild>
        <Link to="/admin/knowledge">返回知识库</Link>
      </Button>
    </div>
  );
}
