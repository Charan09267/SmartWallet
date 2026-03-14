import { Link } from "react-router-dom";

function Dashboard() {
  return (
    <div>
      <h1>Dashboard</h1>

      <h2>Wallet Balance: ₹0</h2>

      <Link to="/add-money">
        <button>Add Money</button>
      </Link>

      <Link to="/withdraw">
        <button>Withdraw</button>
      </Link>

      <Link to="/transactions">
        <button>Transactions</button>
      </Link>
    </div>
  );
}

export default Dashboard;
