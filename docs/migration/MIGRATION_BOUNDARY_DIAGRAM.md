# Migration Boundary Diagram

## Application Architecture Overview

The following diagram illustrates the three-layer architecture of the SampleMvcWebApp application, showing dependencies between layers, external dependencies for each layer, and the proposed migration order.

```mermaid
flowchart TB
    subgraph Legend["Legend"]
        direction LR
        L1[" "] --> L2["Migration Order"]
        style L1 fill:#e8f5e9
        style L2 fill:#fff
    end

    subgraph Phase4["Phase 4: Presentation Layer Migration (Week 5)"]
        direction TB
        subgraph SampleWebApp["SampleWebApp (Presentation Layer)"]
            direction TB
            Controllers["Controllers<br/>- HomeController<br/>- PostsController<br/>- PostsAsyncController<br/>- TagsController<br/>- TagsAsyncController<br/>- BlogsController"]
            Views["Views<br/>- Razor Views (.cshtml)<br/>- _Layout.cshtml<br/>- EditorTemplates"]
            Infrastructure["Infrastructure<br/>- WebUiInitialise<br/>- AutofacDi<br/>- DiModelBinder<br/>- ValidationHelper"]
            AppStart["App_Start<br/>- RouteConfig<br/>- BundleConfig<br/>- FilterConfig"]
            Config["Configuration<br/>- Web.config<br/>- Global.asax"]
        end
    end

    subgraph Phase3["Phase 3: Service Layer Migration (Weeks 3-4)"]
        direction TB
        subgraph ServiceLayer["ServiceLayer (Business Logic)"]
            direction TB
            DTOs["DTOs<br/>- DetailPostDto<br/>- SimplePostDto<br/>- BlogListDto<br/>- TagListDto"]
            UiClasses["UI Classes<br/>- DropDownListType<br/>- MultiSelectListType"]
            ServiceStartup["Startup<br/>- ServiceLayerModule<br/>- ServiceLayerInitialise"]
        end
    end

    subgraph Phase2["Phase 2: Data Layer Migration (Week 2)"]
        direction TB
        subgraph DataLayer["DataLayer (Data Access)"]
            direction TB
            Entities["Entities<br/>- Blog<br/>- Post<br/>- Tag<br/>- TrackUpdate"]
            DbContext["DbContext<br/>- SampleWebAppDb"]
            DataConfig["Configuration<br/>- EfConfiguration"]
            DataStartup["Startup<br/>- DataLayerModule<br/>- DataLayerInitialise"]
        end
    end

    subgraph ExternalDeps["External Dependencies"]
        direction TB
        subgraph WebDeps["SampleWebApp Dependencies"]
            ASPNETMVC["ASP.NET MVC 5.2.3"]
            AutofacMvc["Autofac.Mvc5 3.3.1"]
            Identity["ASP.NET Identity 2.1.0"]
            SignalR["SignalR 2.0.3"]
            OWIN["OWIN 3.0.0"]
            Log4Net["log4net 2.0.3"]
        end
        
        subgraph ServiceDeps["ServiceLayer Dependencies"]
            GenericServices["GenericServices 1.0.9"]
            GenericLibsBase["GenericLibsBase 1.0.1"]
            AutoMapper["AutoMapper 4.2.1"]
            Autofac["Autofac 3.5.0"]
        end
        
        subgraph DataDeps["DataLayer Dependencies"]
            EF6["Entity Framework 6.1.3"]
            DelegateDecompiler["DelegateDecompiler 0.18.0"]
        end
    end

    %% Layer Dependencies
    SampleWebApp --> ServiceLayer
    SampleWebApp --> DataLayer
    ServiceLayer --> DataLayer

    %% External Dependencies
    Controllers --> ASPNETMVC
    Infrastructure --> AutofacMvc
    Infrastructure --> OWIN
    Infrastructure --> Log4Net
    Config --> Identity
    Config --> SignalR

    DTOs --> GenericServices
    DTOs --> GenericLibsBase
    DTOs --> AutoMapper
    ServiceStartup --> Autofac

    DbContext --> EF6
    DataConfig --> EF6
    Entities --> DelegateDecompiler
    DataStartup --> Autofac

    %% Styling
    style Phase4 fill:#ffebee,stroke:#c62828
    style Phase3 fill:#fff3e0,stroke:#ef6c00
    style Phase2 fill:#e8f5e9,stroke:#2e7d32
    style SampleWebApp fill:#ffcdd2
    style ServiceLayer fill:#ffe0b2
    style DataLayer fill:#c8e6c9
    style ExternalDeps fill:#e3f2fd,stroke:#1565c0
```

## Detailed Layer Dependency Diagram

```mermaid
flowchart LR
    subgraph Presentation["Presentation Layer"]
        direction TB
        PC[PostsController]
        PAC[PostsAsyncController]
        TC[TagsController]
        TAC[TagsAsyncController]
        BC[BlogsController]
        HC[HomeController]
    end

    subgraph Services["Service Layer"]
        direction TB
        ILS[IListService]
        IDS[IDetailService]
        IDSA[IDetailServiceAsync]
        ICS[ICreateService]
        ICSA[ICreateServiceAsync]
        IUS[IUpdateService]
        IUSA[IUpdateServiceAsync]
        IDELS[IDeleteService]
        IDELSA[IDeleteServiceAsync]
    end

    subgraph Data["Data Layer"]
        direction TB
        SDB[(SampleWebAppDb)]
        Blog[Blog Entity]
        Post[Post Entity]
        Tag[Tag Entity]
    end

    %% Controller to Service dependencies
    PC --> ILS
    PC --> IDS
    PC --> ICS
    PC --> IUS
    PC --> IDELS

    PAC --> ILS
    PAC --> IDSA
    PAC --> ICSA
    PAC --> IUSA
    PAC --> IDELSA

    TC --> ILS
    TC --> IDS
    TC --> ICS
    TC --> IUS
    TC --> IDELS

    TAC --> ILS
    TAC --> IDSA
    TAC --> ICSA
    TAC --> IUSA
    TAC --> IDELSA

    BC --> ILS
    BC --> ICS
    BC --> IUS
    BC --> IDELS

    %% Service to Data dependencies
    ILS --> SDB
    IDS --> SDB
    IDSA --> SDB
    ICS --> SDB
    ICSA --> SDB
    IUS --> SDB
    IUSA --> SDB
    IDELS --> SDB
    IDELSA --> SDB

    %% DbContext to Entity dependencies
    SDB --> Blog
    SDB --> Post
    SDB --> Tag

    %% Entity relationships
    Blog -.->|1:N| Post
    Post -.->|N:M| Tag

    style Presentation fill:#ffcdd2
    style Services fill:#ffe0b2
    style Data fill:#c8e6c9
```

## Migration Flow Diagram

```mermaid
flowchart TD
    subgraph Phase1["Phase 1: Infrastructure (Week 1)"]
        P1A[Convert .csproj to SDK-style]
        P1B[Remove packages.config]
        P1C[Create Program.cs skeleton]
        P1D[Create appsettings.json]
        P1A --> P1B --> P1C --> P1D
    end

    subgraph Phase2["Phase 2: DataLayer (Week 2)"]
        P2A[Update SampleWebAppDb for EF Core]
        P2B[Delete EfConfiguration.cs]
        P2C[Update DataLayerInitialise.cs]
        P2D[Update DataLayerModule.cs]
        P2E[Test database connectivity]
        P2A --> P2B --> P2C --> P2D --> P2E
    end

    subgraph Phase3["Phase 3: ServiceLayer (Weeks 3-4)"]
        P3A[Create MediatR queries/commands]
        P3B[Create handlers]
        P3C[Convert DTOs to plain classes]
        P3D[Create AutoMapper profiles]
        P3E[Implement Result pattern]
        P3F[Update ServiceLayerModule.cs]
        P3A --> P3B --> P3C --> P3D --> P3E --> P3F
    end

    subgraph Phase4["Phase 4: Presentation (Week 5)"]
        P4A[Complete Program.cs]
        P4B[Migrate controllers to constructor DI]
        P4C[Update views]
        P4D[Delete obsolete files]
        P4E[Test all endpoints]
        P4A --> P4B --> P4C --> P4D --> P4E
    end

    subgraph Phase5["Phase 5: Cleanup (Week 6)"]
        P5A[Run all tests]
        P5B[Remove unused packages]
        P5C[Performance testing]
        P5D[Documentation updates]
        P5A --> P5B --> P5C --> P5D
    end

    Phase1 --> Phase2 --> Phase3 --> Phase4 --> Phase5

    style Phase1 fill:#e3f2fd
    style Phase2 fill:#e8f5e9
    style Phase3 fill:#fff3e0
    style Phase4 fill:#ffebee
    style Phase5 fill:#f3e5f5
```

## Current vs Target Architecture

### Current Architecture (.NET Framework 4.5.1)

```mermaid
flowchart TB
    subgraph Current["Current Architecture"]
        direction TB
        
        subgraph IIS["IIS / IIS Express"]
            GlobalAsax["Global.asax<br/>(HttpApplication)"]
        end
        
        subgraph MVC5["ASP.NET MVC 5"]
            WebConfig["Web.config"]
            RouteConfig["RouteConfig.cs"]
            BundleConfig["BundleConfig.cs"]
            DiModelBinder["DiModelBinder<br/>(Action Parameter DI)"]
        end
        
        subgraph DI["Autofac 3.5"]
            AutofacResolver["AutofacDependencyResolver"]
            ServiceModule["ServiceLayerModule"]
            DataModule["DataLayerModule"]
        end
        
        subgraph GenSvc["GenericServices 1.0.9"]
            IListSvc["IListService"]
            ICrudSvc["CRUD Services"]
            EfGenericDto["EfGenericDto Base"]
        end
        
        subgraph EF6["Entity Framework 6.1.3"]
            DbConfig["DbConfiguration"]
            DbCtx["DbContext"]
            LocalDB["LocalDB"]
        end
        
        GlobalAsax --> MVC5
        MVC5 --> DI
        DI --> GenSvc
        GenSvc --> EF6
    end

    style Current fill:#ffebee
    style IIS fill:#ffcdd2
    style MVC5 fill:#ffe0b2
    style DI fill:#fff9c4
    style GenSvc fill:#c8e6c9
    style EF6 fill:#b3e5fc
```

### Target Architecture (.NET Core 6+)

```mermaid
flowchart TB
    subgraph Target["Target Architecture"]
        direction TB
        
        subgraph Kestrel["Kestrel Web Server"]
            ProgramCs["Program.cs<br/>(Minimal Hosting)"]
        end
        
        subgraph AspNetCore["ASP.NET Core MVC"]
            AppSettings["appsettings.json"]
            EndpointRouting["Endpoint Routing"]
            ConstructorDI["Constructor DI<br/>(Built-in + Autofac)"]
        end
        
        subgraph DI["Autofac 8.x + Built-in DI"]
            ServiceProviderFactory["AutofacServiceProviderFactory"]
            ServiceModule["ServiceLayerModule"]
            DataModule["DataLayerModule"]
        end
        
        subgraph MediatR["MediatR + Custom Services"]
            Queries["Query Handlers"]
            Commands["Command Handlers"]
            PlainDTOs["Plain DTOs + AutoMapper"]
        end
        
        subgraph EFCore["Entity Framework Core 8.x"]
            DbCtxOptions["DbContextOptions"]
            DbCtx["DbContext"]
            SqlServer["SQL Server / LocalDB"]
        end
        
        ProgramCs --> AspNetCore
        AspNetCore --> DI
        DI --> MediatR
        MediatR --> EFCore
    end

    style Target fill:#e8f5e9
    style Kestrel fill:#c8e6c9
    style AspNetCore fill:#a5d6a7
    style DI fill:#fff9c4
    style MediatR fill:#b3e5fc
    style EFCore fill:#ce93d8
```

## Entity Relationship Diagram

```mermaid
erDiagram
    Blog ||--o{ Post : "has many"
    Post }o--o{ Tag : "many to many"
    
    Blog {
        int BlogId PK
        string Name
        string EmailAddress
    }
    
    Post {
        int PostId PK
        string Title
        string Content
        int BlogId FK
        datetime LastUpdated
    }
    
    Tag {
        int TagId PK
        string Slug
        string Name
    }
```

## File Migration Status Matrix

```mermaid
flowchart LR
    subgraph DataLayerFiles["DataLayer Files"]
        direction TB
        DL1["DataLayer.csproj<br/>🔴 Replace"]
        DL2["SampleWebAppDb.cs<br/>🔴 Major Refactor"]
        DL3["EfConfiguration.cs<br/>🔴 Delete"]
        DL4["DataLayerInitialise.cs<br/>🔴 Major Refactor"]
        DL5["DataLayerModule.cs<br/>🟡 Update"]
        DL6["Blog.cs<br/>🟢 Minor"]
        DL7["Post.cs<br/>🟢 Minor"]
        DL8["Tag.cs<br/>🟢 Minor"]
    end

    subgraph ServiceLayerFiles["ServiceLayer Files"]
        direction TB
        SL1["ServiceLayer.csproj<br/>🔴 Replace"]
        SL2["DetailPostDto.cs<br/>🔴 Major Refactor"]
        SL3["SimplePostDto.cs<br/>🔴 Major Refactor"]
        SL4["BlogListDto.cs<br/>🔴 Major Refactor"]
        SL5["TagListDto.cs<br/>🔴 Major Refactor"]
        SL6["ServiceLayerModule.cs<br/>🔴 Major Refactor"]
        SL7["DropDownListType.cs<br/>🟢 No Change"]
        SL8["MultiSelectListType.cs<br/>🟢 No Change"]
    end

    subgraph WebAppFiles["SampleWebApp Files"]
        direction TB
        WA1["SampleWebApp.csproj<br/>🔴 Replace"]
        WA2["Global.asax<br/>🔴 Delete"]
        WA3["Web.config<br/>🔴 Replace"]
        WA4["PostsController.cs<br/>🔴 Major Refactor"]
        WA5["WebUiInitialise.cs<br/>🔴 Delete"]
        WA6["DiModelBinder.cs<br/>🔴 Delete"]
        WA7["_Layout.cshtml<br/>🟡 Update"]
        WA8["Other Views<br/>🟢 Minor"]
    end

    style DL1 fill:#ffcdd2
    style DL2 fill:#ffcdd2
    style DL3 fill:#ffcdd2
    style DL4 fill:#ffcdd2
    style DL5 fill:#fff9c4
    style DL6 fill:#c8e6c9
    style DL7 fill:#c8e6c9
    style DL8 fill:#c8e6c9

    style SL1 fill:#ffcdd2
    style SL2 fill:#ffcdd2
    style SL3 fill:#ffcdd2
    style SL4 fill:#ffcdd2
    style SL5 fill:#ffcdd2
    style SL6 fill:#ffcdd2
    style SL7 fill:#c8e6c9
    style SL8 fill:#c8e6c9

    style WA1 fill:#ffcdd2
    style WA2 fill:#ffcdd2
    style WA3 fill:#ffcdd2
    style WA4 fill:#ffcdd2
    style WA5 fill:#ffcdd2
    style WA6 fill:#ffcdd2
    style WA7 fill:#fff9c4
    style WA8 fill:#c8e6c9
```

### Legend
- 🔴 **Red**: High priority - Major changes or deletion required
- 🟡 **Yellow**: Medium priority - Updates needed
- 🟢 **Green**: Low priority - Minor or no changes

## Summary

The migration follows a bottom-up approach, starting with the DataLayer (lowest risk, no upstream dependencies), then ServiceLayer (highest complexity due to GenericServices replacement), and finally the Presentation Layer (depends on both lower layers).

This phased approach allows for:
1. Incremental testing at each phase
2. Rollback capability if issues arise
3. Clear milestones for progress tracking
4. Isolation of high-risk changes (GenericServices replacement)
