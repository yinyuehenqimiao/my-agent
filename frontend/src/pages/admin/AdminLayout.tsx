import { useMemo, useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  Bot,
  ChevronDown,
  ChevronsLeft,
  ChevronsRight,
  Database,
  LogOut,
  Menu,
  type LucideIcon
} from "lucide-react";
import { useAuthStore } from "@/stores/authStore";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { Avatar } from "@/components/common/Avatar";
import { cn } from "@/lib/utils";

type MenuItem = {
  path: string;
  label: string;
  icon: LucideIcon;
};

const menuItems: MenuItem[] = [
  {
    path: "/admin/knowledge",
    label: "知识库",
    icon: Database
  }
];

const breadcrumbMap: Record<string, string> = {
  knowledge: "知识库"
};

export function AdminLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const [collapsed, setCollapsed] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  const breadcrumbs = useMemo(() => {
    const segments = location.pathname.split("/").filter(Boolean);
    const items: { label: string; to?: string }[] = [{ label: "首页", to: "/admin/knowledge" }];
    if (segments[0] !== "admin") return items;
    const section = segments[1];
    if (section) {
      items.push({
        label: breadcrumbMap[section] || section,
        to: `/admin/${section}`
      });
    }
    if (section === "knowledge" && segments.length > 2) {
      items.push({
        label: "文档管理",
        to: segments.length > 3 ? `/admin/knowledge/${segments[2]}` : undefined
      });
    }
    if (section === "knowledge" && segments.includes("docs")) {
      items.push({ label: "分块管理" });
    }
    return items;
  }, [location.pathname]);

  const roleLabel = user?.role === "admin" ? "管理员" : "成员";

  return (
    <div className="admin-layout flex h-screen">
      <aside className={cn("admin-sidebar", collapsed && "admin-sidebar--collapsed")}>
        <div className="admin-sidebar__brand">
          <div className={cn("flex items-center gap-3", collapsed && "justify-center")}>
            <div className="admin-sidebar__logo">
              <Bot className="h-[22px] w-[22px]" />
            </div>
            {!collapsed && (
              <div className="min-w-0">
                <h1 className="admin-sidebar__title">my-agent 管理后台</h1>
                <p className="admin-sidebar__subtitle">Knowledge Console</p>
              </div>
            )}
          </div>
        </div>

        <nav className="flex-1 space-y-4 px-2 pb-4">
          <div className="space-y-2">
            {!collapsed && <p className="admin-sidebar__group-title">导航</p>}
            <div className="space-y-1">
              {menuItems.map((item) => {
                const Icon = item.icon;
                const isActive =
                  location.pathname === item.path || location.pathname.startsWith(`${item.path}/`);
                return (
                  <Link
                    key={item.path}
                    to={item.path}
                    title={collapsed ? item.label : undefined}
                    className={cn(
                      "admin-sidebar__item",
                      isActive && "admin-sidebar__item--active",
                      collapsed && "justify-center"
                    )}
                  >
                    <span
                      className={cn("admin-sidebar__item-indicator", isActive && "is-active")}
                    />
                    <Icon className="admin-sidebar__item-icon" />
                    {collapsed ? <span className="sr-only">{item.label}</span> : <span>{item.label}</span>}
                  </Link>
                );
              })}
            </div>
          </div>
        </nav>

        <div className="admin-sidebar__footer space-y-2">
          <button
            type="button"
            className="admin-sidebar__collapse"
            onClick={() => setCollapsed((prev) => !prev)}
          >
            {collapsed ? <ChevronsRight className="h-4 w-4" /> : <ChevronsLeft className="h-4 w-4" />}
            {!collapsed && <span>收起侧边栏</span>}
          </button>
        </div>
      </aside>

      <div className="admin-main flex min-h-screen flex-1 flex-col overflow-auto">
        <header className="admin-topbar">
          <div className="admin-topbar-inner">
            <div className="flex items-center gap-3">
              <Button
                variant="ghost"
                size="icon"
                className="lg:hidden"
                onClick={() => setCollapsed((prev) => !prev)}
                aria-label="切换侧边栏"
              >
                <Menu className="h-5 w-5" />
              </Button>
              <p className="text-sm text-slate-500">知识库上传与分块</p>
            </div>
            <div className="flex items-center gap-2">
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button
                    type="button"
                    className="flex items-center gap-2 rounded-full border border-slate-200 bg-white px-2.5 py-1.5 text-sm text-slate-600 shadow-sm"
                    aria-label="用户菜单"
                  >
                    <Avatar
                      name={user?.username || "管理员"}
                      className="h-8 w-8 border-slate-200 bg-indigo-50 text-xs font-semibold text-indigo-600"
                    />
                    <span className="hidden sm:inline">{user?.username || "管理员"}</span>
                    <ChevronDown className="h-4 w-4 text-slate-400" />
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" sideOffset={8} className="w-44">
                  <div className="px-3 py-2 text-xs text-slate-500">
                    {user?.username || "管理员"} · {roleLabel}
                  </div>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onClick={handleLogout} className="text-rose-600 focus:text-rose-600">
                    <LogOut className="mr-2 h-4 w-4" />
                    退出登录
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>
        </header>

        <div className="admin-content">
          <nav className="admin-breadcrumbs" aria-label="面包屑">
            {breadcrumbs.map((item, index) => {
              const isLast = index === breadcrumbs.length - 1;
              return (
                <span key={`${item.label}-${index}`} className="flex items-center gap-2">
                  {item.to && !isLast ? (
                    <Link to={item.to}>{item.label}</Link>
                  ) : (
                    <span className={isLast ? "text-slate-700" : undefined}>{item.label}</span>
                  )}
                  {!isLast && <span>/</span>}
                </span>
              );
            })}
          </nav>
          <Outlet />
        </div>
      </div>
    </div>
  );
}
