package ru.stoliarenkoas.tm.webserver.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.annotation.ApplicationScope;
import ru.stoliarenkoas.tm.webserver.api.service.ProjectServicePageable;
import ru.stoliarenkoas.tm.webserver.api.service.TaskServicePageable;
import ru.stoliarenkoas.tm.webserver.api.service.UserServicePageable;
import ru.stoliarenkoas.tm.webserver.exception.AccessForbiddenException;
import ru.stoliarenkoas.tm.webserver.exception.IncorrectDataException;
import ru.stoliarenkoas.tm.webserver.model.dto.ProjectDTO;
import ru.stoliarenkoas.tm.webserver.model.dto.TaskDTO;
import ru.stoliarenkoas.tm.webserver.model.entity.Task;
import ru.stoliarenkoas.tm.webserver.repository.ProjectRepositoryPageable;
import ru.stoliarenkoas.tm.webserver.repository.TaskRepositoryPageable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@ApplicationScope
public class TaskServicePageableImpl implements TaskServicePageable {

    private TaskRepositoryPageable repository;
    @Autowired
    public void setRepository(TaskRepositoryPageable repository) {
        this.repository = repository;
    }

    private UserServicePageable userService;
    @Autowired
    public void setUserService(UserServicePageable userService) {
        this.userService = userService;
    }

    private ProjectServicePageable projectService;
    @Autowired
    public void setProjectService(ProjectServicePageable projectService) {
        this.projectService = projectService;
    }

    private ProjectRepositoryPageable projectRepository;
    @Autowired
    public void setProjectRepository(ProjectRepositoryPageable projectRepository) {
        this.projectRepository = projectRepository;
    }

    @NotNull
    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<TaskDTO> findAllByUserId(@Nullable final String loggedUserId) throws AccessForbiddenException {
        checkAuthorization(loggedUserId);
        final List<Task> tasks = repository.findAllByProject_User_Id(loggedUserId);
        return tasks.stream().map(Task::toDTO).collect(Collectors.toList());
    }

    @NotNull
    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Page<TaskDTO> findAllByUserId(
            @Nullable final String loggedUserId,
            @Nullable PageRequest page
            ) throws AccessForbiddenException {
        checkAuthorization(loggedUserId);
        if (page == null) page = PageRequest.of(0, 10);
        return repository.findAllByProject_User_Id(loggedUserId, page).map(Task::toDTO);
    }

    @NotNull
    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Page<TaskDTO> findAllByProjectId(
            @Nullable final String loggedUserId,
            @Nullable final String projectId,
            @Nullable PageRequest page
            ) throws AccessForbiddenException, IncorrectDataException {
        checkAuthorization(loggedUserId);
        if (projectId == null || projectId.isEmpty() || !projectService.exists(loggedUserId, projectId)) {
            throw new IncorrectDataException("project selection error");
        }
        if (page == null) page = PageRequest.of(0, 10);
        return repository.findAllByProject_Id(projectId, page).map(Task::toDTO);
    }

    @Nullable
    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public TaskDTO findOne(
            @Nullable final String loggedUserId,
            @Nullable final String requestedTaskId
            ) throws AccessForbiddenException, IncorrectDataException {
        checkAuthorization(loggedUserId);
        if (requestedTaskId == null || requestedTaskId.isEmpty()) {
            throw new IncorrectDataException("no task selected");
        }
        return repository.findOne(loggedUserId, requestedTaskId).map(Task::toDTO).orElse(null);
    }

    @NotNull
    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Boolean exists(@Nullable String loggedUserId, @Nullable String requestedTaskId) {
        if (loggedUserId == null || requestedTaskId == null) return false;
        return repository.existsByProject_User_IdAndId(loggedUserId, requestedTaskId);
    }

    @Override
    @Transactional
    public void persist(
            @Nullable final String loggedUserId,
            @Nullable final TaskDTO persistableTask
            ) throws AccessForbiddenException, IncorrectDataException {
        checkAuthorization(loggedUserId);
        checkTask(loggedUserId, persistableTask);
        if (repository.existsById(persistableTask.getId())) {
            throw new AccessForbiddenException("task already exists");
        }
        repository.save(new Task(persistableTask, projectRepository.getOne(persistableTask.getProjectId())));
    }

    @Override
    @Transactional
    public void merge(
            @Nullable final String loggedUserId,
            @Nullable final TaskDTO persistableTask
            ) throws AccessForbiddenException, IncorrectDataException {
        checkAuthorization(loggedUserId);
        checkTask(loggedUserId, persistableTask);
        repository.save(new Task(persistableTask, projectRepository.getOne(persistableTask.getProjectId())));
    }

    @Override
    @Transactional
    public void remove(
            @Nullable final String loggedUserId,
            @Nullable final String removableTaskId
            ) throws AccessForbiddenException, IncorrectDataException {
        checkAuthorization(loggedUserId);
        if (removableTaskId == null || removableTaskId.isEmpty()) {
            throw new IncorrectDataException("empty task");
        }
        final Optional<Task> task = repository.findOne(loggedUserId, removableTaskId);
        if (!task.isPresent()) {
            throw new IncorrectDataException("task doesn't exist");
        }
        repository.delete(task.get());
    }

    @Override
    @Transactional
    public void removeByProjectId(
            @Nullable final String loggedUserId,
            @Nullable final String projectId
            ) throws AccessForbiddenException, IncorrectDataException {
        checkAuthorization(loggedUserId);
        if (projectId == null || projectId.isEmpty()) {
            throw new IncorrectDataException("empty project");
        }
        if (!projectService.exists(loggedUserId, projectId)) {
            throw new IncorrectDataException("project doesn't exist");
        }
        repository.deleteAllByProject_Id(projectId);
    }

    private void checkAuthorization(@Nullable final String loggedUserId) throws AccessForbiddenException {
        if (loggedUserId == null || loggedUserId.isEmpty()) {
            throw new AccessForbiddenException("not authorized");
        }
        if (!userService.exists(loggedUserId)) {
            throw new AccessForbiddenException("no such user");
        }
    }

    private void checkTask(@NotNull final String userId, @Nullable final TaskDTO taskDTO)
            throws AccessForbiddenException, IncorrectDataException {
        if (taskDTO == null) throw new IncorrectDataException("null task");
        if (!userId.equals(taskDTO.getUserId())) throw new AccessForbiddenException("save by wrong user");
        if (taskDTO.getName() == null || taskDTO.getName().isEmpty()) {
            throw new IncorrectDataException("empty task name");
        }
        final ProjectDTO projectDTO = projectService.findOne(userId, taskDTO.getProjectId());
        if (projectDTO == null || !userId.equals(projectDTO.getUserId())) {
            throw new IncorrectDataException("project doesn't exist");
        }
    }

}
