package reaper.controller;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;
import reaper.*;
import reaper.entity.AdminAction;
import reaper.repository.AdminActionRepository;

@RestController
@RequestMapping("/")
public class AdminActionController{
    private static final Logger log = LoggerFactory.getLogger(AdminActionController.class);

    private final AdminActionRepository repository;

    public AdminActionController(@Autowired AdminActionRepository repository){
        this.repository = repository;
    }

    @GetMapping("/actions/{type}")
    public Flux<AdminAction> get(@PathVariable AdminActionType type){
        return repository.findAllByType(type);
    }

    @GetMapping("/actions/{type}/{targetId}")
    public Flux<AdminAction> get(@PathVariable AdminActionType type, @PathVariable String targetId){
        return repository.findByTypeAndTargetId(type, targetId);
    }

    @PostMapping("/actions")
    public Mono<AdminAction> add(@RequestBody AdminAction adminAction){
        return repository.save(adminAction);
    }

    @DeleteMapping("/actions/{type}/{targetId}")
    public Mono<Void> add(@PathVariable AdminActionType type, @PathVariable String targetId){
        return repository.deleteAllByTypeAndTargetId(type, targetId);
    }
}