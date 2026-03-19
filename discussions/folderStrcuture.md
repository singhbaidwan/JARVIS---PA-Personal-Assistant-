
```
jarvis/
│
├── jarvis-runtime/                # 🧠 Entry point (process manager)
│   ├── main.py
│   ├── process_manager.py
│   ├── health_checker.py
│   ├── config_loader.py
│   └── scripts/
│       ├── start.sh
│       ├── stop.sh
│       └── status.sh
│
├── jarvis-core/                   # ⚙️ Kotlin (THE BRAIN)
│   ├── src/main/kotlin/com/jarvis/
│   │
│   │   ├── orchestrator/          # Core brain
│   │   │   ├── Orchestrator.kt
│   │   │   ├── CommandRouter.kt
│   │   │   └── ContextManager.kt
│   │
│   │   ├── decision/              # Decision engine
│   │   │   ├── DecisionEngine.kt
│   │   │   ├── RuleEngine.kt
│   │   │   └── PolicyEngine.kt
│   │
│   │   ├── event/                 # Event system (CRITICAL)
│   │   │   ├── Event.kt
│   │   │   ├── EventBus.kt
│   │   │   ├── EventHandler.kt
│   │   │   └── EventPublisher.kt
│   │
│   │   ├── task/                  # Task + workflow engine
│   │   │   ├── Task.kt
│   │   │   ├── TaskExecutor.kt
│   │   │   ├── Workflow.kt
│   │   │   └── WorkflowEngine.kt
│   │
│   │   ├── execution/             # Execution layer
│   │   │   ├── JobQueue.kt
│   │   │   ├── TaskRunner.kt
│   │   │   └── Scheduler.kt
│   │
│   │   ├── adapters/              # External integrations
│   │   │   ├── ai/                # Python AI service client
│   │   │   │   └── AiClient.kt
│   │   │   ├── mac/               # Swift agent client
│   │   │   │   └── MacAgentClient.kt
│   │   │   └── db/                # Database layer
│   │   │       ├── EventRepository.kt
│   │   │       └── TaskRepository.kt
│   │
│   │   ├── api/                   # REST/gRPC APIs
│   │   │   ├── EventController.kt
│   │   │   ├── TaskController.kt
│   │   │   └── HealthController.kt
│   │
│   │   └── config/
│   │       └── AppConfig.kt
│   │
│   ├── resources/
│   │   ├── application.yml
│   │   └── logback.xml
│   │
│   └── build.gradle.kts
│
├── jarvis-ai/                     # 🧠 Python (AI Engine)
│   ├── app/
│   │   ├── main.py               # FastAPI entry
│   │
│   │   ├── llm/
│   │   │   ├── inference.py
│   │   │   └── prompt_manager.py
│   │
│   │   ├── embeddings/
│   │   │   ├── embedder.py
│   │   │   └── vector_store.py
│   │
│   │   ├── behavior/
│   │   │   ├── pattern_detector.py
│   │   │   ├── predictor.py
│   │   │   └── feature_engineering.py
│   │
│   │   ├── anomaly/
│   │   │   └── anomaly_detector.py
│   │
│   │   ├── api/
│   │   │   ├── routes/
│   │   │   │   ├── llm.py
│   │   │   │   ├── predict.py
│   │   │   │   └── anomaly.py
│   │   │   └── schemas.py
│   │
│   │   ├── services/
│   │   │   └── ai_service.py
│   │
│   │   └── config/
│   │       └── settings.py
│   │
│   ├── models/                   # Saved ML models
│   ├── notebooks/                # Experimentation (Kaggle style)
│   ├── requirements.txt
│   └── Dockerfile (optional later)
│
├── jarvis-agent/                  # 🍏 Swift (macOS integration)
│   ├── Sources/
│   │   ├── main.swift
│   │
│   │   ├── system/
│   │   │   ├── AppMonitor.swift
│   │   │   ├── SystemMetrics.swift
│   │   │   └── IdleTracker.swift
│   │
│   │   ├── events/
│   │   │   ├── EventEmitter.swift
│   │   │   └── EventModels.swift
│   │
│   │   ├── communication/
│   │   │   └── CoreClient.swift   # send events to Kotlin
│   │
│   │   └── config/
│   │       └── Config.swift
│   │
│   └── Package.swift
│
├── jarvis-ui/                     # 🎨 Desktop UI (Tauri + React)
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── hooks/
│   │   ├── services/
│   │   └── store/
│   │
│   ├── public/
│   ├── src-tauri/                # Tauri backend
│   └── package.json
│
├── jarvis-data/                   # 💾 Data layer
│   ├── db/
│   │   └── jarvis.db
│   │
│   ├── migrations/
│   │   └── init.sql
│   │
│   ├── vector_store/
│   └── backups/
│
├── config/                        # ⚙️ Global configs
│   ├── application.yml
│   ├── services.yml
│   └── env/
│       ├── dev.env
│       └── prod.env
│
├── logs/                          # 📜 Centralized logs
│   ├── jarvis-core.log
│   ├── jarvis-ai.log
│   └── jarvis-agent.log
│
├── scripts/                       # 🛠️ Dev scripts
│   ├── start_all.sh
│   ├── stop_all.sh
│   ├── reset_db.sh
│   └── tail_logs.sh
│
├── docs/                          # 📚 Documentation
│   ├── architecture.md
│   ├── api.md
│   └── roadmap.md
│
└── README.md

```