import { Me } from "../App";

export default function ProfilePage({ me }: { me: Me }) {
  const role = String(me.role || "USER").toUpperCase();
  return (
    <div className="panel profile-page">
      <h2>个人资料</h2>
      <table className="profile-table">
        <tbody>
          <tr>
            <th>用户名</th>
            <td>{me.username}</td>
          </tr>
          <tr>
            <th>角色</th>
            <td>{role}</td>
          </tr>
          <tr>
            <th>备注</th>
            <td>{me.note || "-"}</td>
          </tr>
          <tr>
            <th>暴涨暴跌</th>
            <td>{me.volatility_enabled ? "已开启" : "已关闭"}</td>
          </tr>
          <tr>
            <th>ID</th>
            <td className="profile-mono">{me.id}</td>
          </tr>
        </tbody>
      </table>
      <p className="profile-hint">提示：修改资料/密码请在管理员页操作（或后续再加“编辑资料”功能）。</p>
    </div>
  );
}

