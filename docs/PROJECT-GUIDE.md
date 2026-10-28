# Tomato: poora project ek ek karke (kya, kyun, kaise chalta hai)

Ye guide aapke liye hai: interview mein ya khud samajhne ke liye. Har hissa batata hai **kya hai**, **kyun use kiya**, aur **kaise chalta hai**.

---

## 1. Project kya hai (ek line mein)

Ek food delivery website. Customer menu dekhta hai, cart mein dishes daalta hai, COD ya Stripe se order deta hai, email aati hai, aur ek chatbot menu ke sawaal ka jawab deta hai. Admin dishes, orders aur stock sambhalta hai.

Isko **ek bada code nahi**, balki **kai chhote services** mein todha gaya hai (microservices). Sab Docker mein chalte hain, **14 containers**.

---

## 2. Poora naksha

```
Browser
  |
  +--> Customer app (React, port 5173)      +--> Admin panel (React, port 5174)
              \                                   /
               +--------> GATEWAY (Spring Cloud Gateway, port 8090) <--------+
                          login (JWT) yahan check hota hai
                                 |
   +------------+----------------+-----------------+-----------------+
   |            |                |                 |                 |
 Node backend  order-service   inventory-service  ai-service     (notification-service
 (MongoDB)     (Postgres)      (Postgres)         (Postgres)      sirf Kafka sunti hai)
   users,      orders,         stock              recommendations   email bhejti hai
   foods,      payment         reserve/commit     + chatbot
   cart        (Stripe)
                  |
                  +--> Kafka ("order-events") --> inventory, notification, ai sunte hain
```

| Container | Kaam | Technology |
|---|---|---|
| smallapp | customer website | React + Vite, nginx |
| admin | admin panel | React + Vite, nginx |
| gateway | darwaza: login check, sahi service tak bhejna | Spring Cloud Gateway |
| backend | users, dishes, cart | Node.js + Express + MongoDB Atlas |
| order-service | orders, payment, events bhejna | Spring Boot + PostgreSQL |
| inventory-service | stock | Spring Boot + PostgreSQL |
| notification-service | email | Spring Boot + PostgreSQL + MailHog |
| ai-service | recommendations + chatbot | Python FastAPI + PostgreSQL |
| kafka | services ke beech message bus | Apache Kafka (KRaft) |
| mailhog | test mailbox | MailHog |
| 4 x postgres | har service ka apna database | PostgreSQL 16 |

---

## 3. Chalane ka tareeka

### Laptop par (sabse aasaan)
```bash
docker compose up -d --build
```
Pehli baar 10 to 15 minute lagte hain (sab images ban rahi hain). Phir:

| Kya | Address |
|---|---|
| Customer app | http://localhost:5173 |
| Admin panel | http://localhost:5174 |
| Gateway (API) | http://localhost:8090 |
| Test inbox (emails) | http://localhost:8025 |

**Zaroori:** `backend/.env` file chahiye (MongoDB link, JWT secret, Stripe keys). Wo Git mein nahi hoti, kyunki wo secret hai.

Band karna: `docker compose down`.

### Tests
| Kya test hota hai | Command |
|---|---|
| Node backend | `cd backend && npm test` |
| Java services | `cd order-service && ./mvnw test` (aise hi inventory, notification, gateway) |
| AI service | `cd ai-service && python -m pytest` |
| Poora flow browser mein | `cd e2e && npm test` (stack chalu hona chahiye) |

### AWS par
`deploy/` folder: `ec2-bootstrap.sh` (server ek baar taiyaar), `deploy.sh` (update + start + checks). Detail `deploy/README.md` mein hai.

---

## 4. Har hissa ek ek karke

### 4.1 Frontend (smallapp aur admin): React + Vite
- **Kya:** do alag React websites. Customer app (menu, cart, checkout, My Orders, chat) aur Admin (dishes add, orders ka status, stock).
- **Kyun React:** page ke hisse (component) alag-alag bante hain, state (cart) aasaani se sambhalti hai. **Vite** kyunki wo bahut tez build karta hai.
- **Kyun nginx se serve:** build ke baad sirf static files bachti hain. nginx unhe halka aur tez serve karta hai (container sirf 128 MB).
- **Ek khaas baat:** website ke andar gateway ka address **build ke waqt** likh diya jata hai (`VITE_API_URL`), kyunki API browser se bulayi jaati hai, container se nahi. Isi liye AWS par IP badalne par `deploy.sh` dobara chalani padti hai.
- **Naye hisse:** `ChatWidget` (neeche "Ask us" button) aur `AlsoOrdered` (cart mein "Customers also ordered").

### 4.2 Gateway: Spring Cloud Gateway (Java)
- **Kya:** sab requests pehle yahan aati hain (port 8090).
- **Kyun zaroori:** agar har service apna login khud check kare, to har jagah wahi code. Yahan **ek baar** JWT token check hota hai.
- **Kaise:**
  1. Token sahi hai to user ka id aur role `X-User-Id` aur `X-User-Role` header mein daal deta hai.
  2. Aage ki services sirf ye header padhti hain, wo bharosa karti hain ki gateway ne check kar liya.
  3. Route ke hisaab se rules: **PUBLIC** (menu, login), **USER** (cart, order), **ADMIN** (dish add, stock), **BLOCKED** (`/internal/**`).
  4. Rasta badalne wali chaalen (jaise `..` ya `%2e`) reject.
- **Isiliye** order-service, inventory-service aur ai-service ka koi port bahar nahi khula. Wo sirf gateway se baat karte hain.
- **CORS** bhi yahin hota hai (kaun si website API bula sakti hai).

### 4.3 Backend: Node.js + Express + MongoDB Atlas
- **Kya:** users (register/login), dishes (menu), cart.
- **Kyun Node:** ye project ka **pehla** hissa tha (pehle se bana hua), aur MongoDB ke saath aasaan hai. Naye features Java/Python mein alag service bana ke jode gaye, purana code todha nahi.
- **Kyun MongoDB:** dishes aur cart ka data flexible hai (JSON jaisa). Atlas = cloud mein free database, jo laptop aur AWS dono se judta hai.
- **Libraries:** `bcrypt` (password hash, asli password kabhi save nahi hota), `jsonwebtoken` (login token), `multer` (dish ki image upload), `validator` (email check), `jest` + `supertest` (tests).
- **Security:** role kabhi request se nahi aata, sab "user" banke register hote hain. Admin banana alag script se hota hai.
- **Internal routes:** order-service ko cart chahiye hoti hai, wo `/internal/...` se leta hai, jo `INTERNAL_API_KEY` maangta hai aur bahar se band hai.

### 4.4 order-service: Spring Boot + PostgreSQL + Flyway
- **Kya:** order banana, payment, status badalna, "My Orders".
- **Kyun Java/Spring Boot:** paise aur orders ka kaam hai, strong types, transactions, aur testing ke liye achha.
- **Kyun PostgreSQL:** orders **rishton** wala data hai (order, items, payment) aur **ACID transaction** chahiye (ya sab save ya kuch nahi). Mongo se behtar.
- **Flyway:** database ke tables ka version control (`V1__...sql`). Har start par automatic naye changes lagte hain.
- **Order kaise banta hai (COD):**
  1. Cart backend se leta hai, **prices database se** (browser ki price par bharosa nahi, isliye koi total badal nahi sakta).
  2. inventory-service se stock **reserve** karwata hai (hold).
  3. Order aur ek "event" **ek hi transaction** mein save karta hai (neeche outbox dekho).
  4. Cart khali.
- **Stripe:** card payment ke liye Stripe ka page. Payment hone par Stripe **webhook** bhejta hai (signature check hota hai) aur order "paid" ho jata hai.
- **Status:** Payment Pending, Payment Failed, Food Processing, Out for delivery, Delivered. Galat jump (jaise Delivered se wapas) rok diya jata hai (`OrderTransitions`).

### 4.5 Kafka: services ke beech sandesh
- **Kya:** ek "bulletin board" (topic `order-events`). Order service likhti hai, baaki padhti hain.
- **Kyun zaroori:** agar order-service seedha email service ko bulaye aur wo band ho, to order hi atak jata. Kafka mein order **pehle save**, baaki services apni raftaar se kaam karti hain. Ek ka kharab hona doosre ko nahi rokta.
- **Events:** `ORDER_PLACED`, `ORDER_PAID`, `ORDER_PAYMENT_FAILED`, `ORDER_STATUS_CHANGED`. Key = orderId, to ek order ke events ek kram mein rehte hain.
- **KRaft mode:** Kafka bina ZooKeeper ke (kam containers). Ek hi node, to ye demo hai, high availability nahi.
- **Har service ka alag consumer group:** isliye har service **har event padhti hai**.

### 4.5.1 Transactional Outbox (important sawaal)
- **Samasya:** order database mein save hua, par Kafka band tha, to event kho gaya. Ya Kafka ko bheja par database fail. Dono mein gadbad.
- **Hal:** event ko pehle **same transaction mein** `outbox_events` table mein likho. Phir ek alag `OutboxPublisher` us table se Kafka mein bhejta rehta hai. Kafka band ho to event table mein ruka rehta hai, aur baad mein chala jata hai. Order kabhi nahi khota.

### 4.6 inventory-service: stock
- **Kya:** har dish ka stock, aur "Out of stock" badge ka data.
- **Reserve, commit, release:**
  - Order dete waqt units **hold** (reserve).
  - Payment ho gayi (`ORDER_PAID` ya COD) to **commit** (permanent kam).
  - Payment fail ho gayi to **release** (wapas).
- **Kyun events se:** pehle commit/release alag tarah se hota tha. Ab Kafka event se hota hai, to order-service ko inventory ke commit ka intezaar nahi.
- **Idempotent:** wahi event do baar aaye (Kafka kabhi kabhi do baar deta hai) to stock do baar kam nahi hota. Isme event id save hoti hai.
- Jo dish "tracked" nahi hai wo hamesha available hai.

### 4.7 notification-service: email
- **Kya:** order ke events sunti hai aur customer ko email bhejti hai (Order confirmed, Payment received, Payment failed, Out for delivery, Delivered).
- **Kyun alag:** email bhejna dheema aur kabhi fail hone wala kaam hai. Order se alag rakha.
- **MailHog:** development mein asli email nahi jaati, ek **nakli mail server** pakad leta hai, jise http://localhost:8025 par dekh sakte ho.
- **Guarantees:**
  - Ek event se **ek hi email** (event id save).
  - Email fail ho to 1s, 2s, 4s... baad **dobara try** (8 baar).
  - Bahut kharab message **dead letter topic** mein, baaki rukte nahi.

### 4.8 ai-service: Python + FastAPI
**Do kaam.**

**(a) "Customers also ordered" (recommendation)**
- Ye **ginti** hai, AI model nahi. Kafka se confirmed orders padhta hai aur do cheezein ginta hai: kaun si dish kitni baar order hui, aur kaun si do dishes **ek hi order** mein kitni baar aayi.
- Cart khulne par: cart ki dishes ke saath sabse zyada aane wali dishes dikhao (sold out wali nahi).
- **Naye orders se apne aap behtar hota hai.** Koi training ya retraining nahi.

**(b) Chatbot (RAG)**
- **RAG** = pehle jawab ke liye sahi jaankari **dhoondho**, phir **likho**.
- Kaise:
  1. Sawaal aata hai.
  2. **BM25** search (keyword search, `search.py` mein khud likha) menu aur help articles mein sabse milte hue tukde dhoondhta hai.
  3. Wo tukde aur sawaal **Claude** ko diye jaate hain, "sirf is jaankari se jawab do".
  4. Jawab ke saath **sources** bhi wapas aate hain.
- **Kyun seedha Claude se nahi:** Claude ko is restaurant ka menu, price ya stock pata nahi, wo **guess** karega (hallucination). Asli menu dene se jawab sahi hota hai.
- **Kyun BM25, embeddings nahi:** chhota menu hai, BM25 mein koi model download nahi, koi GPU nahi, aur Hinglish words bhi chalte hain.
- **Bina API key ke bhi chalta hai:** key na ho, ya Claude fail ho (network, limit, refusal), to **search ka jawab** dikhata hai ("AI writing is switched off"). Chat kabhi error nahi dikhata.
- **Safety:** question 500 characters tak, sirf pichhle 6 messages, har user ke 20 messages per 10 minute (paise bachane ke liye), aur system prompt mein likha hai ki question/context ko "data" samjho, order ke instruction nahi.
- Claude ka model `ANTHROPIC_MODEL` se badalta hai (default `claude-opus-5-5`, chhota `claude-haiku-5-5` sasta hai).

### 4.9 Databases: kaun kya aur kyun
| Database | Kisme | Kyun |
|---|---|---|
| MongoDB Atlas | users, dishes, cart | flexible JSON data, cloud mein free, pehle se tha |
| PostgreSQL (orders) | orders, outbox | rishte + transactions |
| PostgreSQL (inventory) | stock, reservations | stock mein galti nahi chalti |
| PostgreSQL (notifications) | bheji hui emails | ek event = ek email ka record |
| PostgreSQL (ai) | recommendation ginti | service ka apna data |

**Har service ka apna database** isliye ki ek service doosre ke tables na padh sake aur na tod sake. Ye microservices ka bunyadi niyam hai.

---

## 5. Ek order ka poora safar (sabse zaroori)

COD order, step by step:

1. Customer **"Place Order"** dabata hai. Browser `POST /api/order/placecod` gateway (8090) ko bhejta hai, token ke saath.
2. **Gateway** token check karta hai, `X-User-Id` lagata hai, order-service ko bhejta hai.
3. **order-service** cart leta hai (backend se), prices database se, aur total banata hai.
4. **inventory-service** se stock **reserve** karwata hai. Kam ho to "only N left" ka error.
5. Order aur outbox event **ek transaction** mein Postgres mein save.
6. **OutboxPublisher** event Kafka (`order-events`) mein bhejta hai.
7. Teen services **alag-alag** padhti hain:
   - **inventory** stock commit karti hai,
   - **notification** "Order confirmed" email bhejti hai (MailHog mein dikhti hai),
   - **ai-service** order ko recommendation ginti mein jodti hai.
8. Customer **My Orders** mein order dekhta hai. Admin **Orders** page par status badalta hai, to naya event (`ORDER_STATUS_CHANGED`) jaata hai aur "Out for delivery" ki email jaati hai.

Stripe mein sirf ek farq: pehle "Payment Pending", phir Stripe ka webhook aane par `ORDER_PAID`. Payment fail hui to stock wapas (release) aur "Payment failed" email.

---

## 6. Docker aur Docker Compose
- **Docker:** har service apne **dibbe** (container) mein, apni zaroorat ki sab cheezon ke saath. "Mere laptop par chalta hai" wali dikkat khatam.
- **docker-compose.yml:** ek file mein sab 14 containers, unke ports, passwords, kaun kiske baad start ho (`depends_on` + healthcheck), memory limit.
- **Kuch niyam:** databases, Kafka aur services ka **koi port bahar nahi**. Sirf website (5173, 5174), gateway (8090) aur MailHog (sirf laptop ka) khule hain. Zyada tar containers non-root user se chalte hain.
- **Dockerfile (multi-stage):** pehle build, phir sirf zaroori file final image mein. Isliye image chhoti.

---

## 7. Testing (quality ka saboot)
| Kisme | Kaun sa tool | Kya dekhta hai |
|---|---|---|
| Node | Jest + Supertest | login, register, cart, routes |
| Java services | JUnit + **Testcontainers** | asli PostgreSQL aur Kafka container start karke test (mock nahi) |
| ai-service | pytest + Testcontainers | counting, search, chat, rate limit, Kafka retries |
| Poora system | **Playwright** | asli browser: sign up, order, email MailHog mein, chat, security |

**Testcontainers kyun:** database ke SQL ka sahi chalna **asli** database par hi pata chalta hai.
**Kul 300+ tests.**

---

## 8. CI aur Deploy
- **CI (GitHub Actions, `.github/workflows/ci.yml`):** har pull request par sab tests apne aap chalte hain. Galat code merge hone se pehle pakda jata hai.
- **Workflow ka tareeka:** kaam alag branch par, **Pull Request**, tests pass, phir `main` mein merge.
- **AWS EC2:** ek server, usme wahi Docker Compose. `ec2-bootstrap.sh` (ek baar) aur `deploy.sh` (har update). **Deploy** workflow manual hai (button), taaki bina poochhe paise na lagein.
- **Limit (honest):** sirf HTTP (HTTPS nahi), ek server, ek Kafka node. Ye demo ke liye hai, asli production ke liye nahi.

---

## 9. Security ka summary
- Password **hash** (bcrypt), kabhi plain nahi.
- Token (JWT) gateway par check, role request se nahi aata.
- Prices server par database se, browser ki price par bharosa nahi.
- Internal routes (`/internal`) bahar se band, service-to-service ke liye secret key.
- Stripe webhook ka signature check.
- Secrets (`.env`) Git mein nahi.
- Services aur databases ke ports bahar band.
- Chat par rate limit aur input limit.

---

## 10. Interview ke sawaal aur chhote jawab

**Microservices kyun, ek hi app kyun nahi?**
Practice ke liye aur kyunki alag-alag hisse (email, stock, AI) alag raftaar se chalte hain aur ek ka fail hona doosre ko nahi rokta. (Sach mein itne chhote project ko ek app kafi hota, par ye seekhne ka project hai.)

**Kafka kyun?**
Order ke baad jo kaam hote hain (email, stock, recommendation), wo order ko rokte nahi. Aur nayi service jodna aasaan hai, bas event sunni hai.

**Outbox pattern kya hai?**
Event ko database mein usi transaction mein likho, phir alag se Kafka mein bhejo. Isse order aur event kabhi alag nahi hote.

**Idempotent consumer?**
Wahi message do baar aaye to bhi asar ek hi baar. Event id database mein save karke.

**RAG kya hai, aur kyun?**
Pehle sahi jaankari dhoondho, phir Claude se jawab likhwao, taaki wo guess na kare.

**Recommendation AI hai?**
Seedhi ginti (co-occurrence counting) hai. Model nahi. Chatbot mein Claude hai.

**Agar Claude down ho?**
Chat search se jawab deta hai, error nahi.

**Isme kya kamiyan hain?**
HTTPS nahi, ek Kafka node, demo ka server (hamesha chalu nahi), recommendation sirf ginti pe hai (category/user history ka dhyan nahi), search keyword-based hai (matlab samajhne wala nahi).

**Agle kadam kya honge?**
HTTPS aur domain, images ko registry mein rakhna (ECR), RDS/MSK jaisi managed services, recommendation mein category.
