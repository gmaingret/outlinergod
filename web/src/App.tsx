import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './components/ProtectedRoute'
import { LoginPage } from './components/LoginPage'

// Phase 17/18 placeholders — replaced when those phases ship
function DocumentListPage() {
  return <div className="p-8 text-gray-700">Document list — coming in Phase 17</div>
}

function NodeEditorPage() {
  return <div className="p-8 text-gray-700">Node editor — coming in Phase 18</div>
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<DocumentListPage />} />
          <Route path="/editor/:id" element={<NodeEditorPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
