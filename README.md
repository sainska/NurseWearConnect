## Developer Information

Student: Lynne Harriet Vion
Institution: University of Eastern Africa, Baraton
Course: INSY 492 – Senior Project
Supervisor: Mr. Felix Chepsiror
Instructor: Mr. Omari Dickson

# NurseWear Connect (NWC)

**A Digital Platform for Linking Student Nurses to Verified Uniform Vendors and Streamlining Custom Uniform Ordering**

## Project Overview

NurseWear Connect (NWC) is a mobile and web-based platform developed for the University of Eastern Africa, Baraton. It connects student nurses with verified uniform vendors, enabling efficient, transparent, and convenient ordering of nursing uniforms.

The system eliminates the traditional manual process of finding vendors by providing a centralized digital marketplace for browsing, ordering, customizing, and tracking nursing uniforms.

## Problem Statement

Student nurses face challenges such as:
- Lack of a centralized platform for uniform ordering
- Inconsistent product quality and pricing
- Difficulty identifying trusted vendors
- Poor communication and order tracking
- Measurement and customization errors

## Proposed Solution

NurseWear Connect provides a unified digital platform where:
- Students can browse and order uniforms online
- Vendors can manage products and orders
- Administrators can monitor system activity
- Users can communicate in real-time
- Payments and reviews are handled digitally

## Key Features

### Student Module
- User registration and login
- Browse uniforms (scrubs, lab coats, etc.)
- Add items to cart
- Submit custom measurements and embroidery details
- Place orders and track status
- Make secure payments
- Rate and review vendors
- Chat with vendors

### Vendor Module
- Vendor registration and verification
- Product management (add, update, delete uniforms)
- Order management and status updates
- Communication with students
- View reviews and ratings
- Sales tracking dashboard

### Admin Module
- User and vendor management
- Vendor verification
- System monitoring
- Generate reports
- Manage disputes

## System Architecture

- Frontend (Web): React.js, Bootstrap, JavaScript  
- Mobile App: React Native  
- Backend: Node.js (Express.js)  
- Database: MySQL  
- Authentication: JWT  
- Hosting: cPanel / AWS (optional)  
- Security: SSL encryption  

## Core Modules

- Authentication Module (Login/Register/RBAC)
- Product & Cart Management
- Order Processing System
- Payment Integration Module
- Chat & Messaging System
- Review & Rating System
- Admin Dashboard & Reporting

## Database Overview

Key entities include:
- Users (Students, Vendors, Admins)
- Vendor Profiles
- Products (Uniforms)
- Orders and Order Items
- Payments
- Reviews
- Chat Messages

## Installation Guide

### 1. Clone Repository
```bash
git clone https://github.com/your-username/nursewear-connect.git
cd nursewear-connect
````

### 2. Backend Setup

```bash
cd backend
npm install
npm run dev
```

### 3. Frontend Setup (Web)

```bash
cd frontend
npm install
npm start
```

### 4. Mobile App Setup

```bash
cd mobile
npm install
npx react-native run-android
```

---

## Environment Variables

Create a `.env` file in the backend:

```
DB_HOST=localhost
DB_USER=root
DB_PASSWORD=your_password
DB_NAME=nwc_db
JWT_SECRET=your_secret_key
PAYMENT_API_KEY=your_key
```

## System Objectives

* Digitize nursing uniform procurement
* Improve transparency between students and vendors
* Enable secure online transactions
* Enhance communication and trust
* Streamline order management

## Development Methodology

The project follows an Agile Development Approach:

1. Requirement gathering
2. System design (ERD, UI/UX)
3. Development (Frontend and Backend)
4. Testing (Unit and Integration)
5. Deployment
6. Maintenance and feedback


## Expected Outcomes

* Faster uniform ordering process
* Improved vendor visibility
* Reduced manual errors
* Better communication and tracking
* Increased student satisfaction


## Sustainability Impact

* Environmental: Reduces paper-based processes
* Economic: Improves vendor efficiency and sales
* Social: Builds trust through transparency and reviews
* Institutional: Enhances digital transformation within the university


## Future Enhancements

* AI-based size recommendation system
* Delivery tracking system
* Offline mode support
* Multi-campus expansion
* Integration with university systems


## License

This project is developed for academic purposes only as part of the Senior Project requirement.


## Acknowledgements

Special thanks to nursing students, vendors, lecturers, and supervisors for their guidance and support throughout the development of this project.

