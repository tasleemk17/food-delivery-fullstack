# 🍕 Full Stack Food Delivery Application

A full-stack food delivery web application built using **React.js, Node.js, Express.js, MongoDB, and JWT authentication**. The application provides a complete food ordering experience for customers along with an admin panel for managing food items and orders.

##  Project Overview

This project is designed to provide a complete food ordering platform where users can:

- Browse food items and categories
- Search and explore food products
- Add food items to the cart
- Manage cart items and quantities
- Place food orders
- Complete the payment process
- View and track their orders
- Register and log in securely
- Access their profile and order history

The project also includes a separate **Admin Panel** for managing food items and orders.

---

## ✨ Key Features

### 👤 User Features

- User registration and login
- JWT-based authentication
- Browse food items
- Explore food categories
- Search and filter food items
- Add/remove items from cart
- Update item quantities
- Place orders
- Payment integration
- Order verification
- View previous orders
- User profile and logout functionality

### 🛠️ Admin Panel

- Admin dashboard
- Add new food items
- View food items
- Manage food items
- View customer orders
- Update order status
- Upload food images

### 🔐 Authentication & Security

- JWT-based authentication
- Protected backend routes
- Authentication middleware
- Secure user session handling
- Environment variables for sensitive configuration

---

## 🛠️ Technologies Used

### Frontend

- React.js
- JavaScript (ES6+)
- HTML5
- CSS3
- Vite
- React Router
- Context API
- Axios

### Backend

- Node.js
- Express.js
- REST APIs
- JWT
- Middleware
- Mongoose

### Database

- MongoDB

### Payment

- Razorpay Payment Gateway

### Development Tools

- Git
- GitHub
- VS Code
- Postman
- npm

---

## 📂 Project Structure

```text
food-delivery-fullstack/
│
├── admin/
│   ├── public/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   └── assets/
│   ├── package.json
│   └── vite.config.js
│
├── backend/
│   ├── config/
│   ├── controllers/
│   ├── middleware/
│   ├── models/
│   ├── routes/
│   ├── uploads/
│   ├── package.json
│   └── server.js
│
├── smallapp/
│   ├── public/
│   ├── src/
│   │   ├── assets/
│   │   ├── components/
│   │   ├── context/
│   │   └── pages/
│   ├── package.json
│   └── vite.config.js
│
├── .gitignore
└── README.md
```

---

## ⚙️ Installation & Setup

### 1. Clone the Repository

```bash
git clone https://github.com/tasleemk17/food-delivery-fullstack.git
```

Navigate to the project:

```bash
cd food-delivery-fullstack
```

---

## 🔧 Backend Setup

Navigate to the backend:

```bash
cd backend
```

Install dependencies:

```bash
npm install
```

Create a `.env` file inside the `backend` folder.

Example:

```env
MONGODB_URI=your_mongodb_connection_string
JWT_SECRET=your_jwt_secret
RAZORPAY_KEY_ID=your_razorpay_key
RAZORPAY_KEY_SECRET=your_razorpay_secret
```

> **Note:** Never commit your `.env` file or API credentials to GitHub.

Start the backend server:

```bash
npm start
```

---

## 💻 Frontend Setup

Open a new terminal and navigate to:

```bash
cd smallapp
```

Install dependencies:

```bash
npm install
```

Start the development server:

```bash
npm run dev
```

---

## 🛠️ Admin Panel Setup

Open another terminal:

```bash
cd admin
```

Install dependencies:

```bash
npm install
```

Start the admin application:

```bash
npm run dev
```

---

## 🔄 Application Flow

```text
User
  │
  ▼
React Frontend
  │
  ▼
REST APIs
  │
  ▼
Node.js + Express.js
  │
  ▼
MongoDB
```

For authentication:

```text
User Login
    │
    ▼
Backend Authentication
    │
    ▼
JWT Token
    │
    ▼
Protected Routes
```

---

##  Main Modules

### Customer Application

The customer-facing application provides the complete food ordering workflow:

```text
Home
  ↓
Explore Food
  ↓
Food Details
  ↓
Add to Cart
  ↓
Cart
  ↓
Place Order
  ↓
Payment
  ↓
Order Verification
  ↓
My Orders
```

### Admin Application

```text
Admin Login
    ↓
Dashboard
    ↓
Add Food
    ↓
Food List
    ↓
Orders
    ↓
Update Order Status
```

---

## 🔑 Environment Variables

For security, sensitive credentials are stored in environment variables.

The `.env` file is intentionally excluded from Git using `.gitignore`.

Example:

```env
MONGODB_URI=your_mongodb_uri
JWT_SECRET=your_secret_key
RAZORPAY_KEY_ID=your_key_id
RAZORPAY_KEY_SECRET=your_key_secret
```

Replace the placeholder values with your own credentials.

---

## 📸 Screenshots

Screenshots of the application can be added here to showcase the user interface.

### Customer Application

_Add screenshots of the home page, food listing, cart, checkout, and orders here._

### Admin Panel

_Add screenshots of the admin dashboard, food management, and order management here._

---

## 🎯 What I Worked On

I worked on the development of the full-stack food delivery application, including:

- React.js frontend development
- Reusable React components
- Food listing and filtering functionality
- Cart management
- User authentication
- JWT-based authorization
- REST API integration
- Node.js and Express.js backend development
- MongoDB database integration
- Order management
- Payment gateway integration
- Admin panel functionality
- API testing using Postman

---

## 💡 Project Highlights

- Full-stack web application with separate frontend, backend, and admin applications
- RESTful API architecture
- JWT-based authentication and protected routes
- MongoDB database integration using Mongoose
- Cart and order management
- Online payment integration
- Responsive React user interface
- Separate admin panel for application management

---

## 📚 Learning Outcomes

Through this project, I gained practical experience in:

- Building full-stack web applications
- Developing REST APIs
- Connecting React applications with backend services
- Working with MongoDB and Mongoose
- Implementing authentication and authorization
- Managing application state with React
- Integrating third-party payment services
- Using Git and GitHub for version control
- Testing APIs using Postman

---

## 👩‍💻 Author

**Tasleem Kousar Inamdar**

MCA | Full Stack Developer

### Technologies

`React.js` `JavaScript` `Node.js` `Express.js` `MongoDB` `Mongoose` `JWT` `REST API` `Razorpay` `Git` `GitHub`

---

## ⭐ If you find this project useful

Feel free to explore the repository and review the implementation.
