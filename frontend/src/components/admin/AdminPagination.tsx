import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const PAGE_SIZE_OPTIONS = [10, 20, 50];

export interface AdminPageMeta {
  current: number;
  size: number;
  total: number;
}

interface AdminPaginationProps {
  page: AdminPageMeta;
  onChange: (next: AdminPageMeta) => void;
  disabled?: boolean;
}

export function AdminPagination({ page, onChange, disabled }: AdminPaginationProps) {
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / Math.max(page.size, 1)));
  const current = Math.min(Math.max(page.current, 1), totalPages);
  return (
    <div className="wf-tech-pagination">
      <div className="wf-tech-pagination-meta">
        共 {page.total} 条 · 第 {current}/{totalPages} 页
      </div>
      <div className="wf-tech-pagination-controls">
        <Select
          value={String(page.size)}
          onValueChange={(value) => onChange({ ...page, current: 1, size: Number(value) })}
          disabled={disabled}
        >
          <SelectTrigger className="h-8 w-[96px]" aria-label="每页条数">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {PAGE_SIZE_OPTIONS.map((size) => (
              <SelectItem key={size} value={String(size)}>{size} 条/页</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-8"
          disabled={disabled || current <= 1}
          onClick={() => onChange({ ...page, current: current - 1 })}
        >
          上一页
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-8"
          disabled={disabled || current >= totalPages}
          onClick={() => onChange({ ...page, current: current + 1 })}
        >
          下一页
        </Button>
      </div>
    </div>
  );
}

export { PAGE_SIZE_OPTIONS };
