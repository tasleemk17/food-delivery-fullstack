\# 🍕 Full Stack Food Delivery Application



A full-stack food delivery web application built using \*\*React.js, Node.js, Express.js, MongoDB, and JWT authentication\*\*. The application provides a complete food ordering experience for customers along with an admin panel for managing food items and orders.



\## 🚀 Project Overview



This project is designed to provide a complete food ordering platform where users can:



\- Browse food items and categories

\- Search and explore food products

\- Add food items to the cart

\- Manage cart items and quantities

\- Place food orders

\- Complete the payment process

\- View and track their orders

\- Register and log in securely

\- Access their profile and order history



The project also includes a separate \*\*Admin Panel\*\* for managing food items and orders.



\---



\## ✨ Key Features



\### 👤 User Features



\- User registration and login

\- JWT-based authentication

\- Browse food items

\- Explore food categories

\- Search and filter food items

\- Add/remove items from cart

\- Update item quantities

\- Place orders

\- Payment integration

\- Order verification

\- View previous orders

\- User profile and logout functionality



\### 🛠️ Admin Panel



\- Admin dashboard

\- Add new food items

\- View food items

\- Manage food items

\- View customer orders

\- Update order status

\- Upload food images



\### 🔐 Authentication \& Security



\- JWT-based authentication

\- Protected backend routes

\- Authentication middleware

\- Secure user session handling

\- Environment variables for sensitive configuration



\---



\## 🛠️ Technologies Used



\### Frontend



\- React.js

\- JavaScript (ES6+)

\- HTML5

\- CSS3

\- Vite

\- React Router

\- Context API

\- Axios



\### Backend



\- Node.js

\- Express.js

\- REST APIs

\- JWT

\- Middleware

\- Mongoose



\### Database



\- MongoDB



\### Payment



\- Stripe Checkout (test mode) with server-side payment verification and webhooks



\### Development Tools



\- Git

\- GitHub

\- VS Code

\- Postman

\- npm



\---



\## 📂 Project Structure



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



\---



\## ⚙️ Installation \& Setup



\### 1. Clone the Repository



```bash

git clone https://github.com/tasleemk17/food-delivery-fullstack.git

```



Navigate to the project:



```bash

cd food-delivery-fullstack

```



\---



\## 🔧 Backend Setup



Navigate to the backend:



```bash

cd backend

```



Install dependencies:



```bash

npm install

```



Copy `backend/.env.example` to `backend/.env` and fill in your own values.



Example:



```env

MONGODB\_URI=your\_mongodb\_connection\_string

JWT\_SECRET=your\_jwt\_secret

STRIPE\_SECRET\_KEY=sk\_test\_...

STRIPE\_WEBHOOK\_SECRET=whsec\_...

FRONTEND\_URL=http://localhost:5173

ADMIN\_URL=http://localhost:5174

```



> \*\*Note:\*\* Never commit your `.env` file or API credentials to GitHub.



Start the backend server:



```bash

npm start

```



\---



\## 💻 Frontend Setup



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



\---



\## 🛠️ Admin Panel Setup



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



\---



\## 🔄 Application Flow



```text

User

&#x20; │

&#x20; ▼

React Frontend

&#x20; │

&#x20; ▼

REST APIs

&#x20; │

&#x20; ▼

Node.js + Express.js

&#x20; │

&#x20; ▼

MongoDB

```



For authentication:



```text

User Login

&#x20;   │

&#x20;   ▼

Backend Authentication

&#x20;   │

&#x20;   ▼

JWT Token

&#x20;   │

&#x20;   ▼

Protected Routes

```



\---



\## 📌 Main Modules



\### Customer Application



The customer-facing application provides the complete food ordering workflow:



```text

Home

&#x20; ↓

Explore Food

&#x20; ↓

Food Details

&#x20; ↓

Add to Cart

&#x20; ↓

Cart

&#x20; ↓

Place Order

&#x20; ↓

Payment

&#x20; ↓

Order Verification

&#x20; ↓

My Orders

```



\### Admin Application



```text

Admin Login

&#x20;   ↓

Dashboard

&#x20;   ↓

Add Food

&#x20;   ↓

Food List

&#x20;   ↓

Orders

&#x20;   ↓

Update Order Status

```



\---



\## 🔑 Environment Variables



For security, sensitive credentials are stored in environment variables.



The `.env` file is intentionally excluded from Git using `.gitignore`.



Example:



```env

MONGODB\_URI=your\_mongodb\_uri

JWT\_SECRET=your\_secret\_key

STRIPE\_SECRET\_KEY=sk\_test\_...

STRIPE\_WEBHOOK\_SECRET=whsec\_...

```



Replace the placeholder values with your own credentials.



\---



\## 📸 Screenshots



Screenshots of the application can be added here to showcase the user interface.



\### Customer Application



\_Add screenshots of the home page, food listing, cart, checkout, and orders here.\_



\### Admin Panel



\_Add screenshots of the admin dashboard, food management, and order management here.\_



\---



\## 🎯 What I Worked On



I worked on the development of the full-stack food delivery application, including:



\- React.js frontend development

\- Reusable React components

\- Food listing and filtering functionality

\- Cart management

\- User authentication

\- JWT-based authorization

\- REST API integration

\- Node.js and Express.js backend development

\- MongoDB database integration

\- Order management

\- Payment gateway integration

\- Admin panel functionality

\- API testing using Postman



\---



\## 💡 Project Highlights



\- Full-stack web application with separate frontend, backend, and admin applications

\- RESTful API architecture

\- JWT-based authentication and protected routes

\- MongoDB database integration using Mongoose

\- Cart and order management

\- Online payment integration

\- Responsive React user interface

\- Separate admin panel for application management



\---



\## 📚 Learning Outcomes



Through this project, I gained practical experience in:



\- Building full-stack web applications

\- Developing REST APIs

\- Connecting React applications with backend services

\- Working with MongoDB and Mongoose

\- Implementing authentication and authorization

\- Managing application state with React

\- Integrating third-party payment services

\- Using Git and GitHub for version control

\- Testing APIs using Postman



\---



\## 👩‍💻 Author



\*\*Tasleem Kousar Inamdar\*\*



MCA | Full Stack Developer



\### Technologies



`React.js` `JavaScript` `Node.js` `Express.js` `MongoDB` `Mongoose` `JWT` `REST API` `Stripe` `Jest` `Git` `GitHub`



\---



\## ⭐ If you find this project useful



Feel free to explore the repository and review the implementation.

---

## 🔐 Security Improvements

I improved the security of the application by adding proper JWT authentication and authorization.

User identity is taken from the verified token instead of trusting user details sent from the frontend.

I also added validation for orders, addresses, payment details, and admin actions to prevent invalid or unauthorized requests.

Stripe payments are verified on the backend before marking an order as paid.



### Environment Variables

Create a `.env` file in the backend folder and add the required environment variables.

You can use `.env.example` as a reference for the required configuration.
