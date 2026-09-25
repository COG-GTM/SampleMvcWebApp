# Convenience wrapper: `make up|down|reset|baseline|loadtest|verify` from the repo root.
%:
	@$(MAKE) --no-print-directory -C infra $@

.PHONY: help
help:
	@$(MAKE) --no-print-directory -C infra help
