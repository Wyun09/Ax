//go:build linux

package supervisor

import (
	"fmt"
	"os"
	"strconv"
	"strings"
)

type procMetrics struct {
	processTicks uint64
	totalTicks   uint64
	rssBytes     uint64
}

func readProcMetrics(pid int) (procMetrics, error) {
	processTicks, err := readProcessTicks(pid)
	if err != nil {
		return procMetrics{}, err
	}
	totalTicks, err := readSystemTicks()
	if err != nil {
		return procMetrics{}, err
	}
	rssBytes, err := readRSSBytes(pid)
	if err != nil {
		return procMetrics{}, err
	}
	return procMetrics{processTicks: processTicks, totalTicks: totalTicks, rssBytes: rssBytes}, nil
}

func readProcessTicks(pid int) (uint64, error) {
	data, err := os.ReadFile(fmt.Sprintf("/proc/%d/stat", pid))
	if err != nil {
		return 0, err
	}
	text := string(data)
	end := strings.LastIndex(text, ")")
	if end < 0 {
		return 0, fmt.Errorf("malformed /proc/%d/stat", pid)
	}
	fields := strings.Fields(text[end+1:])
	if len(fields) < 13 {
		return 0, fmt.Errorf("short /proc/%d/stat", pid)
	}
	utime, err := strconv.ParseUint(fields[11], 10, 64)
	if err != nil {
		return 0, err
	}
	stime, err := strconv.ParseUint(fields[12], 10, 64)
	if err != nil {
		return 0, err
	}
	return utime + stime, nil
}

func readSystemTicks() (uint64, error) {
	data, err := os.ReadFile("/proc/stat")
	if err != nil {
		return 0, err
	}
	line, _, _ := strings.Cut(string(data), "\n")
	fields := strings.Fields(line)
	if len(fields) < 5 || fields[0] != "cpu" {
		return 0, fmt.Errorf("malformed /proc/stat cpu line")
	}
	limit := len(fields)
	if limit > 9 {
		limit = 9 // cpu + 8 counters; guest values are already included in user/nice.
	}
	var total uint64
	for _, field := range fields[1:limit] {
		value, err := strconv.ParseUint(field, 10, 64)
		if err != nil {
			return 0, err
		}
		total += value
	}
	return total, nil
}

func readRSSBytes(pid int) (uint64, error) {
	data, err := os.ReadFile(fmt.Sprintf("/proc/%d/status", pid))
	if err != nil {
		return 0, err
	}
	for _, line := range strings.Split(string(data), "\n") {
		if !strings.HasPrefix(line, "VmRSS:") {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 2 {
			break
		}
		kb, err := strconv.ParseUint(fields[1], 10, 64)
		if err != nil {
			return 0, err
		}
		return kb * 1024, nil
	}
	return 0, fmt.Errorf("VmRSS not found for pid %d", pid)
}
