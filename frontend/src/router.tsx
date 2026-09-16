import { Navigate, createBrowserRouter } from "react-router-dom";

import { EngineGate } from "@/components/common/EngineGate";
import { LoginPage } from "@/pages/LoginPage";
import { EnterprisePage } from "@/pages/EnterprisePage";
import { EnterpriseAdminPage } from "@/pages/admin/EnterpriseAdminPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { AdminLayout } from "@/pages/admin/AdminLayout";
import { KnowledgeListPage } from "@/pages/admin/knowledge/KnowledgeListPage";
import { KnowledgeDocumentsPage } from "@/pages/admin/knowledge/KnowledgeDocumentsPage";
import { KnowledgeChunksPage } from "@/pages/admin/knowledge/KnowledgeChunksPage";
import { IntentTreePage } from "@/pages/admin/intent-tree/IntentTreePage";
import { IntentListPage } from "@/pages/admin/intent-tree/IntentListPage";
import { IntentEditPage } from "@/pages/admin/intent-tree/IntentEditPage";
import { WorkflowListPage } from "@/pages/admin/workflow/WorkflowListPage";
import { WorkflowDesignerPage } from "@/pages/admin/workflow/WorkflowDesignerPage";
import { ToolCenterPage, AuditPage, RouteSimulationPage } from "@/pages/admin/workflow/WorkflowAdminPages";
import { AgentProfilePage } from "@/pages/admin/agents/AgentProfilePage";
import { AgentPromptPage } from "@/pages/admin/agents/AgentPromptPage";
import { TraceAuditPage } from "@/pages/admin/audit/TraceAuditPage";
import { useAuthStore } from "@/stores/authStore";

function RequireAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role !== "admin") {
    return <Navigate to="/chat" replace />;
  }

  return children;
}

function RedirectIfAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (isAuthenticated) {
    return <Navigate to="/chat" replace />;
  }
  return children;
}

function HomeRedirect() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return <Navigate to={isAuthenticated ? "/chat" : "/login"} replace />;
}

export const router = createBrowserRouter([
  { path: "/enterprise", element: <RequireAuth><EnterprisePage /></RequireAuth> },
  {
    path: "/",
    element: <HomeRedirect />
  },
  {
    path: "/login",
    element: (
      <RedirectIfAuth>
        <LoginPage />
      </RedirectIfAuth>
    )
  },
  {
    path: "/chat",
    element: (
      <RequireAuth>
        <EngineGate />
      </RequireAuth>
    )
  },
  {
    path: "/chat/:sessionId",
    element: (
      <RequireAuth>
        <EngineGate />
      </RequireAuth>
    )
  },
  {
    path: "/admin",
    element: (
      <RequireAdmin>
        <AdminLayout />
      </RequireAdmin>
    ),
    children: [
      { path: "enterprise", element: <EnterpriseAdminPage /> },
      {
        index: true,
        element: <Navigate to="/admin/knowledge" replace />
      },
      {
        path: "knowledge",
        element: <KnowledgeListPage />
      },
      {
        path: "knowledge/:kbId",
        element: <KnowledgeDocumentsPage />
      },
      {
        path: "knowledge/:kbId/docs/:docId",
        element: <KnowledgeChunksPage />
      },
      {
        path: "knowledge/:kbId/docs/:docId/chunks",
        element: <KnowledgeChunksPage />
      },
      {
        path: "agents",
        element: <AgentProfilePage />
      },
      {
        path: "agents/:agentId",
        element: <AgentPromptPage />
      },
      {
        path: "intent-tree",
        element: <IntentTreePage />
      },
      {
        path: "intent-list",
        element: <IntentListPage />
      },
      {
        path: "intent-list/:id/edit",
        element: <IntentEditPage />
      },
      {
        path: "workflow",
        element: <WorkflowListPage />
      },
      {
        path: "workflow/new",
        element: <WorkflowDesignerPage />
      },
      {
        path: "workflow/tools",
        element: <ToolCenterPage />
      },
      {
        path: "workflow/simulate",
        element: <RouteSimulationPage />
      },
      {
        path: "workflow/audit",
        element: <AuditPage />
      },
      {
        path: "workflow/traces",
        element: <TraceAuditPage />
      },
      {
        path: "workflow/:defKey",
        element: <WorkflowDesignerPage />
      }
    ]
  },
  {
    path: "*",
    element: <NotFoundPage />
  }
]);

export { RequireAuth, RequireAdmin };
