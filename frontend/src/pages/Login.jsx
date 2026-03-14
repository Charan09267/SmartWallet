import { Link } from "react-router-dom";

function Login() {
  return (
    <div>
      <h2>Login Page</h2>

      <input placeholder="Email" />
      <input placeholder="Password" type="password" />

      <button>Login</button>

      <p>
        Don't have an account? <Link to="/register">Register</Link>
      </p>
    </div>
  );
}

export default Login;
