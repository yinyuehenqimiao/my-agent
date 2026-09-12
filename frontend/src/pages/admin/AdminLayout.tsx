import { useEffect, useMemo, useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  Bot,
  ChevronDown,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  ClipboardList,
  Database,
  GitBranch,
  Layers,
  LogOut,
  Menu,
  MessageSquare,
  Shield,
  Wrench,
  Workflow,
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

type MenuChild = {
  path: string;
  label: string;
  icon: LucideIcon;
};

type MenuItem = {
  id?: string;
  path: string;
  label: string;
  icon: LucideIcon;
  children?: MenuChild[];
};

const menuItems: MenuItem[] = [
  { path: "/admin/enterprise", label: "企业配置", icon: ClipboardList },
  {
    path: "/chat",
    label: "智能体对话",
    icon: MessageSquare
  },
  {
    path: "/admin/agents",
    label: "智能体管理",
    icon: Bot
  },
  {
    path: "/admin/knowledge",
    label: "知识库",
    icon: Database
  },
  {
    id: "intent",
    path: "/admin/intent-tree",
    label: "意图管理",
    icon: Layers,
    children: [
      { path: "/admin/intent-tree", label: "意图树配置", icon: GitBranch },
      { path: "/admin/intent-list", label: "意图列表", icon: ClipboardList }
    ]
  },
  {
    id: "workflow",
    path: "/admin/workflow",
    label: "工作流中心",
    icon: Workflow,
    children: [
      { path: "/admin/workflow", label: "工作流列表", icon: GitBranch },
      { path: "/admin/workflow/tools", label: "工具与连接器", icon: Wrench },
      { path: "/admin/workflow/simulate", label: "路由模拟", icon: ClipboardList },
      { path: "/admin/workflow/audit", label: "审计查询", icon: Shield }
    ]
  }
];

const breadcrumbMap: Record<string, string> = {
  enterprise: "企业配置",
  knowledge: "知识库",
  agents: "智能体管理",
  "intent-tree": "意图树配置",
  "intent-list": "意图列表",
  workflow: "工作流中心",
  "workflow/tools": "工具与连接器",
  "workflow/simulate": "路由模拟",
  "workflow/audit": "审计查询"
};

export function AdminLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const [collapsed, setCollapsed] = useState(false);
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({ intent: true });

  const isIntentActive =
    location.pathname.startsWith("/admin/intent-tree") ||
    location.pathname.startsWith("/admin/intent-list");

  const isWorkflowActive = location.pathname.startsWith("/admin/workflow");

  useEffect(() => {
    setOpenGroups((prev) => ({
      ...prev,
      intent: prev.intent || isIntentActive,
      workflow: prev.workflow || isWorkflowActive
    }));
  }, [isIntentActive, isWorkflowActive]);

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  const breadcrumbs = useMemo(() => {
    const segments = location.pathname.split("/").filter(Boolean);
    const items: { label: string; to?: string }[] = [{ label: "首页", to: "/admin/knowledge" }];
    if (segments[0] !== "admin") return items;
    const section = segments[1];
    if (!section) return items;

    if (section === "intent-tree" || section === "intent-list") {
      items.push({ label: "意图管理", to: "/admin/intent-tree" });
      if (section === "intent-list" && segments.includes("edit")) {
        items.push({ label: breadcrumbMap[section] || section, to: "/admin/intent-list" });
        items.push({ label: "编辑节点" });
      } else {
        items.push({ label: breadcrumbMap[section] || section });
      }
      return items;
    }

    if (section === "workflow") {
      items.push({ label: "工作流中心", to: "/admin/workflow" });
      const sub = segments[2];
      if (sub === "new") {
        items.push({ label: "新建工作流" });
      } else if (sub && breadcrumbMap[`${section}/${sub}`]) {
        items.push({ label: breadcrumbMap[`${section}/${sub}`] });
      } else if (sub) {
        items.push({ label: `编辑 ${sub}` });
      }
      return items;
    }

    items.push({
      label: breadcrumbMap[section] || section,
      to: `/admin/${section}`
    });

    if (section === "knowledge" && segments.length > 2) {
      items.push({
        label: "文档管理",
        to: segments.length > 3 ? `/admin/knowledge/${segments[2]}` : undefined
      });
    }
    if (section === "knowledge" && segments.includes("docs")) {
      items.push({ label: "分块管理" });
    }
    if (section === "agents" && segments.length > 2) {
      items.push({ label: "提示词配置" });
    }
    return items;
  }, [location.pathname]);

  const roleLabel = user?.role === "admin" ? "管理员" : "成员";

  const renderNavItem = (item: MenuItem) => {
    const Icon = item.icon;

    if (item.children && item.children.length > 0) {
      const groupOpen = item.id ? openGroups[item.id] : false;
      const groupActive = item.id === "intent" && isIntentActive;

      return (
        <div key={item.id || item.path} className="space-y-1">
          <button
            type="button"
            title={collapsed ? item.label : undefined}
            onClick={() => {
              if (collapsed) {
                navigate(item.children![0].path);
                return;
              }
              if (item.id) {
                setOpenGroups((prev) => ({ ...prev, [item.id!]: !prev[item.id!] }));
              }
            }}
            className={cn(
              "admin-sidebar__item w-full",
              groupActive && "admin-sidebar__item--group-active",
              collapsed && "justify-center"
            )}
          >
            <span
              className={cn(
                "admin-sidebar__item-indicator",
                groupActive && "is-group-active"
              )}
            />
            <Icon className="admin-sidebar__item-icon" />
            {!collapsed && (
              <>
                <span className="flex-1 text-left">{item.label}</span>
                {groupOpen ? (
                  <ChevronDown className="h-4 w-4 shrink-0 opacity-60" />
                ) : (
                  <ChevronRight className="h-4 w-4 shrink-0 opacity-60" />
                )}
              </>
            )}
          </button>
          {!collapsed && groupOpen && (
            <div className="ml-4 space-y-1 border-l border-slate-200 pl-2">
              {item.children.map((child) => {
                const ChildIcon = child.icon;
                const childActive =
                  location.pathname === child.path ||
                  location.pathname.startsWith(`${child.path}/`);
                return (
                  <Link
                    key={child.path}
                    to={child.path}
                    className={cn(
                      "admin-sidebar__item text-[13px]",
                      childActive && "admin-sidebar__item--active"
                    )}
                  >
                    <span
                      className={cn(
                        "admin-sidebar__item-indicator",
                        childActive && "is-active"
                      )}
                    />
                    <ChildIcon className="admin-sidebar__item-icon h-4 w-4" />
                    <span>{child.label}</span>
                  </Link>
                );
              })}
            </div>
          )}
        </div>
      );
    }

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
        <span className={cn("admin-sidebar__item-indicator", isActive && "is-active")} />
        <Icon className="admin-sidebar__item-icon" />
        {collapsed ? <span className="sr-only">{item.label}</span> : <span>{item.label}</span>}
      </Link>
    );
  };

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
                <p className="admin-sidebar__subtitle">Agent Console</p>
              </div>
            )}
          </div>
        </div>

        <nav className="flex-1 space-y-4 px-2 pb-4">
          <div className="space-y-2">
            {!collapsed && <p className="admin-sidebar__group-title">导航</p>}
            <div className="space-y-1">{menuItems.map(renderNavItem)}</div>
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
              <p className="text-sm text-slate-500">智能体 · 意图 · 知识库</p>
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
