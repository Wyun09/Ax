package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/Wyun09/Ax/agent/internal/config"
	"github.com/Wyun09/Ax/agent/internal/httpapi"
	"github.com/Wyun09/Ax/agent/internal/supervisor"
)

const version = "0.1.0"

func main() {
	configPath := flag.String("config", "ax.json", "path to Ax agent configuration")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("load config: %v", err)
	}

	manager, err := supervisor.NewManager(cfg.Services)
	if err != nil {
		log.Fatalf("create supervisor: %v", err)
	}

	server := &http.Server{
		Addr:              cfg.Listen,
		Handler:           httpapi.New(manager, version),
		ReadHeaderTimeout: 5 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go func() {
		log.Printf("Ax agent %s listening on http://%s", version, cfg.Listen)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Printf("http server: %v", err)
			stop()
		}
	}()

	<-ctx.Done()
	fmt.Println()
	log.Print("shutting down")

	manager.StopAll()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		log.Printf("http shutdown: %v", err)
	}
}
