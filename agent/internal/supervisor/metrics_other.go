//go:build !linux

package supervisor

import "errors"

type procMetrics struct {
	processTicks uint64
	totalTicks   uint64
	rssBytes     uint64
}

func readProcMetrics(_ int) (procMetrics, error) {
	return procMetrics{}, errors.New("process metrics are only available on Linux")
}
